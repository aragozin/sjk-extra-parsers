package org.perfkit.sjk.jfr.mcparser;

import java.io.IOException;

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

	public void encodeEvent(IItem event, JsonStreamWriter writer) throws IOException {
		writer.writeStartObject();
		try {
			writer.writeStringField("eventType", event.getType().getIdentifier());
			encodeObject(event, writer);			
		}
		finally {
			writer.writeEndObject();
		}
	}

	@SuppressWarnings("rawtypes")
	private void encodeObject(IItem obj, JsonStreamWriter writer) throws IOException {
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
				encodeValue(val, writer);
			}
		}
	}
	
	private void encodeFieldValue(String field, Object val, JsonStreamWriter writer) throws IOException {
		writer.writeFieldName(field);
		encodeValue(val, writer);
	}
	
	private void encodeValue(Object val, JsonStreamWriter writer) throws IOException {
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
				writer.writeNumberField("osThreadId", thread.hashCode()); // hack for MC API
				writer.writeStringField("javaName", thread.getThreadName());
				encodeFieldValue("javaThreadId", thread.getThreadId(), writer);
				encodeFieldValue("group", thread.getThreadGroup(), writer);
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCThreadGroup) {
			IMCThreadGroup tg = (IMCThreadGroup)val;
			writer.writeStartObject();
			try {
				encodeFieldValue("parent", tg.getParent(), writer);
				writer.writeStringField("name", tg.getName());
			}
			finally {
				writer.writeEndObject();
			}
		}
		else if (val instanceof IMCStackTrace) {
			IMCStackTrace trace = (IMCStackTrace)val;
			writer.writeStartObject();
			try {
				writer.writeBooleanField("truncated", trace.getTruncationState().isTruncated());
				writer.writeFieldName("frames");
				writer.writeStartArray();
				try {
					for(IMCFrame frame: trace.getFrames()) {
						encodeValue(frame, writer);
					}					
				}
				finally {
					writer.writeEndArray();
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
				encodeFieldValue("method", frame.getMethod(), writer);
				encodeFieldValue("lineNumber", frame.getFrameLineNumber(), writer);
				encodeFieldValue("bytecodeIndex", frame.getBCI(), writer);
				encodeFieldValue("type", frameType(frame.getType()), writer);
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
