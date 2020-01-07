/**
 * Copyright 2017 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.jvmtool.nps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.gridkit.jvmtool.codec.stacktrace.ThreadSnapshotEvent;
import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventDumpParser;
import org.gridkit.jvmtool.event.EventDumpParser.InputStreamSource;
import org.gridkit.jvmtool.stacktrace.StackFrame;
import org.gridkit.jvmtool.event.EventReader;
import org.gridkit.jvmtool.nps.parser.DirectNpsEventAdapter;
import org.junit.Assert;
import org.junit.Test;

public class SnapshotDataTest {

    @Test
    public void test_load() throws IOException {

        final File npsFile = new File("src/test/resources/hz1.nps");
        Assert.assertTrue("hz1.nps should be present", npsFile.isFile());

        ServiceLoader<EventDumpParser> loader = ServiceLoader.load(EventDumpParser.class);
        boolean found = false;
        for(EventDumpParser edp: loader) {
            if (edp instanceof NetbeansSnapshotParserLoader) {
                found = true;
                System.out.println(edp);
                EventReader<Event> er = edp.open(new InputStreamSource() {

                    @Override
                    public InputStream open() throws IOException {
                        return new FileInputStream(npsFile);
                    }
                });
                Assert.assertNotNull(er);
                histo(er);
            }
        }
        Assert.assertTrue("NetbeansSnapshotParserLoader should be listed", found);
    }

    private void histo(EventReader<Event> er) {
        Map<String, Long> histo = new TreeMap<String, Long>();
        for (Event evt: er) {
            ThreadSnapshotEvent te = (ThreadSnapshotEvent) evt;
            String path = "";
            for(StackFrame sf: te.stackTrace()) {
                path = sf.getMethodName() + "/" + path;
            }
            long w = te.counters().getValue(DirectNpsEventAdapter.NODE_WEIGHT);
            if (w < 0) {
                w = 1;
            }
            if (histo.containsKey(path)) {
                histo.put(path, histo.get(path) + w);
            }
            else {
                histo.put(path, w);
            }
        }

        for(Map.Entry<String, Long> e: histo.entrySet()) {
            System.out.println(String.format("%-40s - %d", e.getKey(), e.getValue()));
        }
    }
}
