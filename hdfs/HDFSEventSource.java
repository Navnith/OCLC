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
 */
/**
 * Start reading the file out of HDFS Hadoop filesystem, find the corresponding path of the file.
 * Parse the file, each line convert to Event event.
 * Ensure the file is open. Deserialize the data and write it to the local channel on the filesystem system.
 * TODO:
 *  1. Use Hadoop API version 2.6.0-cdh5.4.3
 *  2. Use hadoop.fs filesystem API
 *  3. Do we need a FileChecksum?
 *  4. Use org.apache.hadoop.fs.Path to get a directory path on HDFS
 *  5. Implement an ignorePattern regex which files to retrieve/read on HDFS. Skip the file matches both ignorePattern and includePattern
 *  6. Then fill header with Timestamp, filename, length....
 *
 *
 *  Think About: What if we have multiple agents reading Same Directory.
 */
public class HDFSEventSource extends AbstractSource implements PollableSource, Configurable {

    public static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HDFSEventSource.class);

    public static String uri ="";

    FileSystem fileSystem;
    FileStatus[] list;


    @Override
    public void configure(Context context) {
        uri=context.getString("filepath");
        if(uri==null){
            throw new IllegalArgumentException("Cant Find Filepath Parameter");
        }
        LOG.info("adapter will now use {} for its folder", uri.toString());
        Configuration configuration = new Configuration();
        configuration.addResource("core-site.xml");
        configuration.addResource("hdfs-site.xml");

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

        HDFSEventSource call= new HDFSEventSource(); /* mv back to start. */
        ChannelDemo cp= new ChannelDemo();
        call.setChannelProcessor(cp);
    }

    @Override
    public Status process(){
        Status status=null;
        try {
            if (fileSystem.exists(new Path(uri))) {
                list = fileSystem.listStatus(new Path(uri));
                browse(list);
                status = Status.READY;
               // LOG.info("Found the Path and Files to be read are listed" + fileSystem);
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

    private void browse(FileStatus[] list) throws IOException, InterruptedException {
        for (int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                //LOG.info("Is a SubDirectory" + list[i]);
            }
            else{
                String filename = String.valueOf(list[i].getPath());
                if (!(filename.endsWith(".tgz") || filename.endsWith(".gz") || filename.toLowerCase().endsWith(".zip") || filename.toLowerCase().endsWith(".done") || filename.endsWith(".DS_Store"))) {
                    if (!list[i].isFile()) {
                        //LOG.info("File not found" + list[i]);
                    }
                    else{
                        FSDataInputStream fsin = fileSystem.open(list[i].getPath());
                        Map<String, String> header = new HashMap<String, String>();
                        byte dataBuff[] = new byte[(int) list[i].getLen()];
                        fsin.readFully(dataBuff);

                        header.put("FileName",String.valueOf(list[i].getPath()));
                        header.put("TimeStamp",String.valueOf(list[i].getModificationTime()));
                        header.put("FileLength",String.valueOf(dataBuff.length));

                        Event event = EventBuilder.withBody(dataBuff,header);
                        System.out.println("Byte body record " + event);
                        getChannelProcessor().processEvent(event);
                        LOG.info("{} is processed." , list[i].getPath());
                        final boolean rename = fileSystem.rename(list[i].getPath(), new Path(new String(list[i].getPath() + ".done")));
                        if(rename){
                        //    LOG.info("Renamed" + list[i].getPath());
                        }
                        else{
                          //  LOG.info("Cannot Rename File" + list[i]);
                        }
                        //status = Status.READY;
                    }
                }
                else{
                    if (filename.toLowerCase().endsWith(".done") || filename.endsWith(".DS_Store")) {

                    }
                    else {
                        LOG.info("Zipped File" + list[i]);
                    }
                }
            }
        }
    }
}

