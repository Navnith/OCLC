package org.oclc.ingest.hdfs;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractPollableSource;
import org.apache.flume.source.AbstractSource;

import org.apache.flume.source.PollableSourceRunner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.log4j.Logger;
import org.apache.flume.Context;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.apache.flume.PollableSource.Status.BACKOFF;

/**
 * Created by ramkrisn on 6/15/17.
 * Flume Version - 1.6.0
 * Hadoop Version - 2.6.0
 * HADOOP_HOME is required to be setup for running the Flume agent
 * This is a custom Flume source class, to read events (files) from HDFS directory
 * Require a Flume config agent file to be provided, set the property name type as org.oclc.ingest.hdfs.HDFSEventSource
 * Make sure to include hdfs & core-site.xml configs for hadoop.
 * Copy the jar of this class into $FLUME_HOME/plugins.d
 * Example:
 * Start a Flume process as: bin/flume-ng agent --conf conf --conf-file conf/custom_source.conf -C /Users/ramkrisn/Documents/Projects/Ingest/Ingest-imakeup/src/main/resources/confs/hdfs_conf/ --name a1 -Dflume.root.logger=INFO,console
 */

public class HDFSEventSource extends AbstractSource implements PollableSource, Configurable {

    public static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HDFSEventSource.class);

    //URI Stores the location of the Directory with Slurp Files to be read. 
    public static String uri ="";

    FileSystem fileSystem;
    FileStatus[] list; //Maintains the Entire list of files to be read. 


    @Override
    public void configure(Context context) {
        uri=context.getString("filepath"); //Obtaining Location from Flume Config file. 
        if(uri==null){
            throw new IllegalArgumentException("Cant Find Filepath Parameter");
        }
        LOG.info("adapter will now use {} for its folder", uri.toString());
        Configuration configuration = new Configuration();
        configuration.addResource("core-site.xml");
        configuration.addResource("hdfs-site.xml"); //Need to specify path of these 2 files in Flume Command. 

        LOG.info("HDFSEventSource starting");
        try {
            fileSystem=FileSystem.get(URI.create(uri),configuration);
            LOG.info("Found the Config Settings" + fileSystem);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FlumeException("Error cannot start HDFSEventSource!");
        }

    }

    @Override
    public void start() {

        HDFSEventSource call= new HDFSEventSource(); 
        ChannelDemo cp= new ChannelDemo();
        call.setChannelProcessor(cp);
    }

    @Override
    public Status process(){
        Status status=null;
        try {
            if (fileSystem.exists(new Path(uri))) {
                list = fileSystem.listStatus(new Path(uri));
                readFiles(list);
                status = Status.READY;
            } else {
                LOG.info("File or Directory Doesn't exist" + fileSystem);
            }
        }catch (Throwable t){
            status= Status.BACKOFF;
            t.printStackTrace();
        }
        return status;
    }

    @Override
    public void stop(){
        try {
            fileSystem.closeAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
    }

    private void readFiles(FileStatus[] list) throws IOException, InterruptedException {
        boolean dir=false;
        for (int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                //Note Doesn't read contents of Sub Directories. Can be modified by a recursive call here. 
            }
            else{
                String filename = String.valueOf(list[i].getPath());
                //Ignoring Zipped Files and Files already read indicative by .done extension
                if (!(filename.endsWith(".tgz") || filename.endsWith(".gz") || filename.toLowerCase().endsWith(".zip") || filename.toLowerCase().endsWith(".done"))) {
                    if (!list[i].isFile()) {
                        dir=true;
                    }
                    else{
                        FSDataInputStream fsin = fileSystem.open(list[i].getPath());
                        Map<String, String> header = new HashMap<String, String>();
                        byte dataBuff[] = new byte[(int) list[i].getLen()];
                        fsin.readFully(dataBuff);

                        header.put("DirectoryStatus",String.valueOf(dir));
                        header.put("FilePath",String.valueOf(list[i].getPath()));
                        header.put("FileLength",String.valueOf(dataBuff.length));
                        header.put("TimeStamp",String.valueOf(list[i].getModificationTime()));
                        header.put("Owner", list[i].getOwner());
                        header.put("Permission", String.valueOf(list[i].getPermission()));

                        Event event = EventBuilder.withBody(dataBuff,header);
                        System.out.println("Byte body record " + event);
                        getChannelProcessor().processEvent(event);
                        LOG.info("{} is processed." , list[i].getPath());
                        //Appending Extension .done to imply it has been read. 
                        final boolean rename = fileSystem.rename(list[i].getPath(), new Path(new String(list[i].getPath() + ".done")));
                    }
                }
                else{
                    if (filename.toLowerCase().endsWith(".done")) {
                        //Accounting for completed files skipped.
                    }
                    else {
                        LOG.info("Zipped File" + list[i]);
                    }
                }
            }
        }
    }
}

