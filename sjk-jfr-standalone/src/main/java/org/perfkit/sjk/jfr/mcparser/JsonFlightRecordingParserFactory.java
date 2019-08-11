package org.perfkit.sjk.jfr.mcparser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.gridkit.jvmtool.spi.parsers.InputStreamSource;
import org.gridkit.jvmtool.spi.parsers.JsonEventDumpParser;
import org.gridkit.jvmtool.spi.parsers.JsonEventDumpParserFactory;
import org.gridkit.jvmtool.spi.parsers.JsonEventSource;
import org.gridkit.jvmtool.util.json.JsonStreamWriter;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.flightrecorder.internal.EventArray;

public class JsonFlightRecordingParserFactory implements JsonEventDumpParserFactory {

    @Override
    public JsonEventDumpParser createParser(Map<String, String> options) throws Exception {

        Parser parser = new Parser();
        parser.configure(options);

        return parser;

    }

    private static class Parser implements JsonEventDumpParser {

        private int jsonMaxDepth = Integer.MAX_VALUE;
        private List<String> whiteList = null;
        private List<String> blackList = null;

        @Override
        public JsonEventSource open(InputStreamSource source) throws Exception {

            EventArray[] events = JFRLoader.loadStream(IOHelper.openFlatOrCommpressedStream(source), false, true);
            List<IItem> items = new ArrayList<>();
            for(EventArray a: events) {
                for(IItem i: a.getEvents()) {
                    items.add(i);
                }
            }

            EventSource eventSource = new EventSource(items, jsonMaxDepth);
            if (whiteList != null) {
                eventSource.setWhiteList(whiteList);
            }
            if (blackList != null) {
                eventSource.setBlackList(blackList);
            }

            return eventSource;
        }

        public void configure(Map<String, String> options) {
            for(String key: options.keySet()) {
                if (OPT_USE_NATIVE_JFR_PARSER.equals(key)) {
                    if (!"false".equalsIgnoreCase(options.get(key))) {
                        throw new RuntimeException("Unsatisfied option: " + key + "=" + options.get(key));
                    }
                }
                else if (OPT_JSON_MAX_DEPTH.equals(key)) {
                    jsonMaxDepth = Integer.valueOf(options.get(key));
                    if (jsonMaxDepth < 0) {
                        throw new RuntimeException("Illegal option value: " + key + "=" + options.get(key));
                    }
                }
                else if (OPT_JFR_EVENT_BLACKLIST.equals(key)) {
                    String[] list = options.get(key).split(",");
                    if (list.length > 0) {
                        blackList = Arrays.asList(list);
                    }
                }
                else if (OPT_JFR_EVENT_WHITELIST.equals(key)) {
                    String[] list = options.get(key).split(",");
                    if (list.length > 0) {
                        whiteList = Arrays.asList(list);
                    }
                }
                else {
                    throw new RuntimeException("Unknown option: " + key + "=" + options.get(key));
                }
            }
        }
    }

    private static class EventSource implements JsonEventSource {

        private final List<IItem> items;
        private final JsonEventAdapter adapter;
        private int n;

        public EventSource(List<IItem> items, int maxJsonDepth) {
            this.items = items;
            this.adapter = new JsonEventAdapter(maxJsonDepth);
        }

        public void setWhiteList(Collection<String> list) {
            adapter.setWhiteList(list);
        }

        public void setBlackList(Collection<String> list) {
            adapter.setBlackList(list);
        }

        @Override
        public boolean readNext(JsonStreamWriter writer) throws IOException {
            while(true) {
                if (n >= items.size()) {
                    return false;
                }
                IItem it = items.get(n);
                ++n;
                if (!adapter.encodeEvent(it, writer)) {
                    continue;
                };
                return true;
            }
        }
    }
}
