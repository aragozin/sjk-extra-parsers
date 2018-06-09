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
package org.gridkit.jvmtool.jfr.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.gridkit.jvmtool.codec.stacktrace.ThreadSnapshotEventPojo;
import org.gridkit.jvmtool.codec.stacktrace.ThreadTraceEvent;
import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventMorpher;
import org.gridkit.jvmtool.event.EventReader;
import org.gridkit.jvmtool.event.GenericEvent;
import org.gridkit.jvmtool.event.MorphingEventReader;
import org.gridkit.jvmtool.event.SimpleErrorEvent;
import org.gridkit.jvmtool.stacktrace.StackFrame;
import org.gridkit.jvmtool.stacktrace.StackFrameArray;
import org.gridkit.jvmtool.stacktrace.StackFrameList;

import com.jrockit.mc.common.IMCFrame;
import com.jrockit.mc.flightrecorder.FlightRecording;
import com.jrockit.mc.flightrecorder.internal.model.FLRStackTrace;
import com.jrockit.mc.flightrecorder.internal.model.FLRThread;
import com.jrockit.mc.flightrecorder.internal.model.FLRType;
import com.jrockit.mc.flightrecorder.spi.IEvent;

public class JfrEventAdapter implements EventReader<Event> {

	private static final String METHOD_PROFILING_SAMPLE = "Method Profiling Sample";
	private static final String ALLOCATION_IN_NEW_TLAB = "Allocation in new TLAB";
	
	private final Iterator<IEvent> it;

	private ThreadSnapshotEventPojo threadPojo = new ThreadSnapshotEventPojo();
	private NewTLABEventPojo allocPojo = new NewTLABEventPojo();
	private Event event = null;
	private boolean error;
	
	public JfrEventAdapter(FlightRecording recording) {
		this.it = recording.createView().iterator();
	}

	@Override
	public Iterator<Event> iterator() {
		return this;
	}

	private void seek() {
		if (error) {
			return;
		}
		try {
			while(it.hasNext()) {
				IEvent e = it.next();
				String type = e.getEventType().getName();
				if (METHOD_PROFILING_SAMPLE.equals(type)) {
					event = parseThreadSample(e);
					return;
				}
				else if (ALLOCATION_IN_NEW_TLAB.equals(type)) {
					event = parseAllocation(e);
					return;
				}
			}
		}
		catch(Exception e) {
			event = new SimpleErrorEvent(e);
			error = true;
		}
	}

	private Event parseThreadSample(IEvent e) {
		
		threadPojo.timestamp(TimeUnit.NANOSECONDS.toMillis(e.getStartTimestamp()));
		FLRThread thread = (FLRThread) e.getValue("(thread)");
		if (thread != null) {
			threadPojo.threadId(thread.getThreadId());
			threadPojo.threadName(thread.getThreadName());
		}
		else {
			threadPojo.threadId(-1);
			threadPojo.threadName(null);
		}
		FLRStackTrace flrStackTrace = (FLRStackTrace) e.getValue("(stackTrace)");
		if (flrStackTrace != null) {
			threadPojo.stackTrace(trace(flrStackTrace));
		}
		else {
			threadPojo.stackTrace(null);
		}
		
		return threadPojo;
	}

	private Event parseAllocation(IEvent e) {
		allocPojo.counters().clear();
		allocPojo.tags().clear();

		allocPojo.tags().put("jfr.event", "");
		allocPojo.tags().put("jfr.event.name", e.getEventType().getName());

		allocPojo.timestamp(TimeUnit.NANOSECONDS.toMillis(e.getStartTimestamp()));
		FLRThread thread = (FLRThread) e.getValue("(thread)");
		if (thread != null) {
			allocPojo.counters().set("jfr.threadId", thread.getThreadId());
			allocPojo.tags().put("jfr.threadName", thread.getThreadName());
		}
		FLRStackTrace flrStackTrace = (FLRStackTrace) e.getValue("(stackTrace)");
		if (flrStackTrace != null) {
			allocPojo.stackTrace(trace(flrStackTrace));
		}
		else {
			allocPojo.stackTrace(null);
		}
		FLRType type = (FLRType) e.getValue("class");
		if (type != null) {
			allocPojo.tags().put("jfr.allocClass", name(type));
		}
		Long allocaSize = (Long) e.getValue("allocationSize");
		if (allocaSize != null) {
			allocPojo.counters().set("jfr.allocationSize", allocaSize);
		}
		Long tlabSize = (Long) e.getValue("tlabSize");
		if (tlabSize != null) {
			allocPojo.counters().set("jfr.tlabSize", tlabSize);
		}
		
		return allocPojo;
	}
	
	private String name(FLRType type) {
		String name = (type.getPackageName().length() == 0 ? "" : type.getPackageName()) 
				+ "." + type.getTypeName() + 
				(type.getIsArray() ? "[]" : "");
		return name;
	}

	private StackFrameList trace(FLRStackTrace trace) {
		StackFrame[] t = new StackFrame[trace.getFrames().size()];
		for(int i = 0; i != t.length; ++i) {
			t[i] = frame(trace.getFrames().get(i));
		}
		
		return new StackFrameArray(t);
	}

	private StackFrame frame(IMCFrame f) {
		String pn = f.getMethod().getPackageName();
		String cn = f.getMethod().getClassName();
		String mn = f.getMethod().getMethodName();
		int line = (f.getFrameLineNumber() == null) ? -1 : f.getFrameLineNumber();
		if (f.getMethod().getIsNative()) {
			line = -2;
		}
		String src = f.getMethod().getFileName();
		if (src == null && line >= 0) {
			src = "line"; // fake source to make line numbers visible 
		}

		return new StackFrame(pn, cn, mn, src, line);
	}

	@Override
	public boolean hasNext() {
		if (event == null) {
			seek();
		}
		return event != null;
	}

	@Override
	public Event next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		Event e = event;
		event = null;		
		return e;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <M extends Event> EventReader<M> morph(EventMorpher<Event, M> morpher) {
		return new MorphingEventReader<M>(this, morpher);
	}

	@Override
	public Event peekNext() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		return event;
	}

	@Override
	public void dispose() {
		// do nothing
	}
	
	private static class NewTLABEventPojo extends GenericEvent implements ThreadTraceEvent {

		private StackFrameList trace;
		
		@Override
		public StackFrameList stackTrace() {
			return trace;
		}
		
		public void stackTrace(StackFrameList trace) {
			this.trace = trace;
		}		
	}	
}
