package org.perfkit.sjk.jfr.mcparser;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.gridkit.jvmtool.util.json.JsonStreamWriter;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCFrame.Type;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.IMCThread;
import org.openjdk.jmc.common.IMCThreadGroup;
import org.openjdk.jmc.common.IMCType;
import org.openjdk.jmc.common.item.Attribute;
import org.openjdk.jmc.common.item.IAccessorKey;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.unit.ITypedQuantity;
import org.openjdk.jmc.common.unit.TimestampUnit;

public class JsonEventAdapter {

	private final int maxDepth;

	private Set<String> whiteList;
	private Set<String> blackList;
	
	public JsonEventAdapter() {
		this(Integer.MAX_VALUE);
	}

	public JsonEventAdapter(int maxDepth) {
		this.maxDepth = maxDepth;
	}
	
	public void setWhiteList(Collection<String> events) {
		whiteList = new HashSet<String>(events);
	}
	
	public void setBlackList(Collection<String> events) {
		blackList = new HashSet<String>(events);
	}

	public void encodeEvent(IItem event, JsonStreamWriter writer) throws IOException {
		writer.writeStartObject();
		try {
			String eventType = event.getType().getIdentifier();
			if (shouldOutput(eventType)) {
				writer.writeStringField("eventType", event.getType().getIdentifier());
				encodeObject(event, writer, 1);
			}
		}
		finally {
			writer.writeEndObject();
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

	private boolean checkDepthLimit(JsonStreamWriter writer, int depth) throws IOException {
		if (depth > maxDepth) {
			writer.writeStringField("json_depth_limit_reached", "!");
			return false; 
		}
		else {
			return true;
		}		
	}
	
	@SuppressWarnings("rawtypes")
	private void encodeObject(IItem obj, JsonStreamWriter writer, int depth) throws IOException {
		if (!checkDepthLimit(writer, depth)) {
			return;
		}
		ITypedQuantity startTime = null;
		for(IAccessorKey<?> k: obj.getType().getAccessorKeys().keySet()) {
			String name = k.getIdentifier();
			if (k instanceof Attribute) {
				Attribute<?> a = (Attribute<?>) k;
				Object val = a.getAccessor(obj.getType()).getMember(cast(obj));
				if ("startTime".equals(name)) {
					startTime = (ITypedQuantity) val;
				}
				else if ("(endTime)".equals(name)) {
					if (startTime == null) {
						name = "startTime";
					}
					else {
						name = "duration";
						val = ((Number)val).longValue() - ((Number)startTime).longValue();
					}
				}
				writer.writeFieldName(name);
				encodeValue(val, writer, depth);
			}
		}
	}
	
	private void encodeFieldValue(String field, Object val, JsonStreamWriter writer, int depth) throws IOException {
		writer.writeFieldName(field);
		encodeValue(val, writer, depth);
	}
	
	private void encodeValue(Object val, JsonStreamWriter writer, int depth) throws IOException {
 		if (val == null) {
			writer.writeNull();
		}
		else if (val instanceof TimestampUnit) {
			writer.writeString(val.toString());
		}
		else if (val instanceof Number) {
			// TODO potential double precision error
			Number num = (Number) val;
			if (num.longValue() == num.doubleValue()) {
				writer.writeNumber(num.longValue());
			}
			else {
				writer.writeNumber(num.doubleValue());
			}
		}
		else if (val instanceof IMCThread) {
			IMCThread thread = (IMCThread) val;
			writer.writeStartObject();
			try {
				if (checkDepthLimit(writer, depth)) {
					writer.writeNumberField("osThreadId", thread.hashCode()); // hack for MC API
					writer.writeStringField("javaName", thread.getThreadName());
					encodeFieldValue("javaThreadId", thread.getThreadId(), writer, depth + 1);
					encodeFieldValue("group", thread.getThreadGroup(), writer, depth + 1);
				}
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCThreadGroup) {
			IMCThreadGroup tg = (IMCThreadGroup)val;
			writer.writeStartObject();
			try {
				if (checkDepthLimit(writer, depth)) {
					encodeFieldValue("parent", tg.getParent(), writer, depth + 1);
					writer.writeStringField("name", tg.getName());
				}
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCStackTrace) {
			IMCStackTrace trace = (IMCStackTrace)val;
			writer.writeStartObject();
			try {
				if (checkDepthLimit(writer, depth)) {
					writer.writeBooleanField("truncated", trace.getTruncationState().isTruncated());
					writer.writeFieldName("frames");
					writer.writeStartArray();
					try {					
						for(IMCFrame frame: trace.getFrames()) {
							encodeValue(frame, writer, depth + 2);
						}					
					}
					finally {
						writer.writeEndArray();
					}
				}
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCFrame) {
			IMCFrame frame = (IMCFrame) val;
			writer.writeStartObject();
			try {
				if (checkDepthLimit(writer, depth)) {
					encodeFieldValue("method", frame.getMethod(), writer, depth + 1);
					encodeFieldValue("lineNumber", frame.getFrameLineNumber(), writer, depth + 1);
					encodeFieldValue("bytecodeIndex", frame.getBCI(), writer, depth + 1);
					encodeFieldValue("type", frameType(frame.getType()), writer, depth + 1);
				}
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCMethod) {
			IMCMethod method = (IMCMethod) val;
			writer.writeStartObject();
			try {				
				writer.writeStringField("class", method.getType().getFullName());
				writer.writeStringField("method", method.getMethodName());
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCType) {
			IMCType type = (IMCType) val;
			writer.writeStartObject();
			try {
				// TODO add JFR11 classloader and module info				
				writer.writeStringField("className", type.getFullName());
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof Boolean) {
			writer.writeBoolean(Boolean.TRUE.equals(val));
		}
		else if (val instanceof String) {
			writer.writeString(val.toString());
		}
		else {
			writer.writeString(val.toString());
		}
	}
	
	private String frameType(Type type) {
		switch(type) {
			case JIT_COMPILED: return "JIT compiled";
			case INLINED: return "Inlined";
			case INTERPRETED: return "Interpreted";
			default: return "Native"; // TODO Native Vs. Unknown 
		}		
	}

	@SuppressWarnings("unchecked")
	private <T> T cast(Object o) {
		return (T)o;
	}
}
