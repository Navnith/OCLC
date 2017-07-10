package org.oclc.ingest.hdfs;

import org.apache.flume.Channel;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.channel.ChannelProcessor;

import java.util.List;

/**
 * Created by ramkrisn on 6/21/17.
 */
public class ChannelDemo extends ChannelProcessor {
    public ChannelDemo() {
        super(new ChannelSelector() {
            @Override
            public void setChannels(List<Channel> list) {

            }

            @Override
            public List<Channel> getRequiredChannels(Event event) {
                return null;
            }

            @Override
            public List<Channel> getOptionalChannels(Event event) {
                return null;
            }

            @Override
            public List<Channel> getAllChannels() {
                return null;
            }

            @Override
            public void setName(String s) {

            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public void configure(Context context) {

            }
        });

    }
    @Override
    public void processEvent(Event event) {
        System.out.println(new String(event.getBody()));
    }
}
