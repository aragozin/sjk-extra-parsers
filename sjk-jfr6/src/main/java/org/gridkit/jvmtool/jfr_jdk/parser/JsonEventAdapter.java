package org.gridkit.jvmtool.jfr_jdk.parser;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.gridkit.jvmtool.util.json.JsonStreamWriter;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedObject;

public class JsonEventAdapter {

    private final int depthThreshold;
    private Set<String> whiteList;
    private Set<String> blackList;

    public JsonEventAdapter() {
        this(Integer.MAX_VALUE);
    }

    public JsonEventAdapter(int jsonMaxDepth) {
        this.depthThreshold = jsonMaxDepth;
    }

    public void setWhiteList(Collection<String> events) {
        whiteList = new HashSet<String>(events);
    }

    public void setBlackList(Collection<String> events) {
        blackList = new HashSet<String>(events);
    }

    public boolean encodeEvent(RecordedEvent event, JsonStreamWriter writer) throws IOException {
        String eventType = event.getEventType().getName();
        if (shouldOutput(eventType)) {
            writer.writeStartObject();
            try {
                writer.writeStringField("eventType", event.getEventType().getName());
                encodeObject(event, writer, 0);
            }
            finally {
                writer.writeEndObject();
            }
            return true;
        }
        else {
        	return false;
        }
    }

    private boolean shouldOutput(String eventType) {
        if (whiteList != null && !whiteList.contains(eventType)) {
            return false;
        }
        if (blackList != null && blackList.contains(eventType)) {
            return false;
        }
        return true;
    }

    private void encodeObject(RecordedObject obj, JsonStreamWriter writer, int depth) throws IOException {
        if (obj instanceof RecordedMethod) {
            // actual method representation is too verbose
            RecordedMethod rm = (RecordedMethod) obj;
            String mclass = rm.getType().getName();
            String mmethod = rm.getName();
            writer.writeStringField("class", mclass);
            writer.writeStringField("method", mmethod);
            writer.writeBooleanField("hidden", rm.isHidden());
        }
        else {

            if (obj instanceof RecordedClass) {
                RecordedClass rc = (RecordedClass) obj;

                writer.writeStringField("className", rc.getName());
                return; // skip class details so far
            }

            for(ValueDescriptor vd: obj.getFields()) {
                String name = vd.getName();
                Object val = obj.getValue(name);
                if (obj instanceof RecordedEvent) {
                    // use Instant for timestamps
                    if ("startTime".equals(name)) {
                        val = ((RecordedEvent) obj).getStartTime();
                    }
                    else if ("endTime".equals(name)) {
                        val = ((RecordedEvent) obj).getEndTime();
                    }
                }
                if (val instanceof Object[]) {
                    writer.writeFieldName(name);
                    writer.writeStartArray();
                    try {
                        for(Object e: (Object[])val) {
                            if (e instanceof RecordedObject) {
                                if (depth < depthThreshold) {
                                    writer.writeStartObject();
                                    try {
                                        encodeObject((RecordedObject) e, writer, depth + 1);
                                    }
                                    finally {
                                        writer.writeEndObject();
                                    }
                                }
                                else {
                                    writer.writeString("(JSON depth exceeded!)");
                                }
                            }
                            else {
                                encodeValue(e, writer);
                            }
                        }
                    }
                    finally {
                        writer.writeEndArray();
                    }
                }
                else if (val instanceof RecordedObject) {
                    writer.writeFieldName(name);
                    if (depth < depthThreshold) {
                        writer.writeStartObject();
                        try {
                            encodeObject((RecordedObject)val, writer, depth + 1);
                        }
                        finally {
                            writer.writeEndObject();
                        }
                    }
                    else {
                        writer.writeString("(JSON depth exceeded!)");
                    }
                }
                else {
                    writer.writeFieldName(name);
                    encodeValue(val, writer);
                }
            }
        }
    }

    private void encodeValue(Object val, JsonStreamWriter writer) throws IOException {
        if (val == null) {
            writer.writeNull();
        }
        else if (val instanceof Float || val instanceof Double) {
            writer.writeNumber(((Number)val).doubleValue());
        }
        else if (val instanceof Character) {
            writer.writeString(val.toString());
        }
        else if (val instanceof Number) {
            writer.writeNumber(((Number) val).longValue());
        }
        else if (val instanceof Boolean) {
            writer.writeBoolean(Boolean.TRUE.equals(val));
        }
        else if (val instanceof Instant) {
            long epochNs = TimeUnit.SECONDS.toNanos(((Instant) val).getEpochSecond());
            epochNs += ((Instant) val).getNano();
            writer.writeNumber(epochNs);
        }
        else {
            writer.writeString(val.toString());
        }
    }
}
