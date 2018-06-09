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
package org.gridkit.jvmtool.jfr6.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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

import com.oracle.jmc.common.IMCFrame;
import com.oracle.jmc.common.IMCStackTrace;
import com.oracle.jmc.common.IMCThread;
import com.oracle.jmc.common.IMCType;
import com.oracle.jmc.common.IMemberAccessor;
import com.oracle.jmc.common.item.Attribute;
import com.oracle.jmc.common.item.IAccessorFactory;
import com.oracle.jmc.common.item.IItem;
import com.oracle.jmc.common.item.IType;
import com.oracle.jmc.common.unit.IQuantity;
import com.oracle.jmc.common.unit.LinearKindOfQuantity;
import com.oracle.jmc.common.unit.UnitLookup;
import com.oracle.jmc.flightrecorder.JfrAttributes;
import com.oracle.jmc.flightrecorder.internal.EventArray;

public class JfrEventAdapter implements EventReader<Event> {

	private static final String EXECUTION_SAMPLING = "com.oracle.jdk.ExecutionSample";
	private static final String ALLOCATION_IN_NEW_TLAB = "com.oracle.jdk.ObjectAllocationInNewTLAB";
	
	private static final IAccessorFactory<IMCType> ATTR_OBJECT_CLASS = Attribute.attr("objectClass", UnitLookup.CLASS);
	private static final IAccessorFactory<IQuantity> ATTR_ALLOC_SIZE = Attribute.attr("allocationSize", new LinearKindOfQuantity("memory", "byte"));
	private static final IAccessorFactory<IQuantity> ATTR_TLAB_SIZE = Attribute.attr("tlabSize", new LinearKindOfQuantity("memory", "byte"));
	
	private static final List<String> EVENTS = Arrays.asList(EXECUTION_SAMPLING, ALLOCATION_IN_NEW_TLAB);
	
	private final Iterator<IItem> it;

	private ThreadSnapshotEventPojo threadPojo = new ThreadSnapshotEventPojo();
	private NewTLABEventPojo allocPojo = new NewTLABEventPojo();
	private Event event = null;
	private boolean error;

	private EventAttr<IQuantity> E_TIMESTAMP = new EventAttr<IQuantity>(JfrAttributes.EVENT_TIMESTAMP);
	private EventAttr<IMCThread> E_THREAD = new EventAttr<IMCThread>(JfrAttributes.EVENT_THREAD);
	private EventAttr<IMCStackTrace> E_STACK_TRACE = new EventAttr<IMCStackTrace>(JfrAttributes.EVENT_STACKTRACE); 
	private EventAttr<IMCType> E_OBJECT_CLASS = new EventAttr<IMCType>(ATTR_OBJECT_CLASS); 
	private EventAttr<IQuantity> E_ALLOC_SIZE = new EventAttr<IQuantity>(ATTR_ALLOC_SIZE); 
	private EventAttr<IQuantity> E_NEW_TLAB_SIZE = new EventAttr<IQuantity>(ATTR_TLAB_SIZE); 
	
	public JfrEventAdapter(EventArray[] recording) {
		List<IItem> events = new ArrayList<IItem>();
		for(EventArray ea: recording) {
			if (EVENTS.contains(ea.getType().getIdentifier())) {
				events.addAll(Arrays.asList(ea.getEvents()));
			}
		}
		this.it = events.iterator();
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
				IItem e = it.next();
				if (EXECUTION_SAMPLING.equals(e.getType().getIdentifier())) {
					event = parseMethodSamplingEvent(e);					
					return;
				}
				if (ALLOCATION_IN_NEW_TLAB.equals(e.getType().getIdentifier())) {
					event = parseAllocationEvent(e);
				}
			}
		}
		catch(Exception e) {
			event = new SimpleErrorEvent(e);
			error = true;
		}
	}

	private Event parseMethodSamplingEvent(IItem e) {
		threadPojo.timestamp(timestamp(e));
		IMCThread thread = thread(e);
		if (thread != null) {
			threadPojo.threadId(thread.getThreadId());
			threadPojo.threadName(thread.getThreadName());
		}
		else {
			threadPojo.threadId(-1);
			threadPojo.threadName(null);
		}
		IMCStackTrace flrStackTrace = stacktrace(e);
		if (flrStackTrace != null) {
			threadPojo.stackTrace(trace(flrStackTrace));
		}
		else {
			threadPojo.stackTrace(null);
		}
		return threadPojo;
	}

	private Event parseAllocationEvent(IItem e) {
		allocPojo.counters().clear();
		allocPojo.tags().clear();
		
		allocPojo.tags().put("jfr.event", "");
		allocPojo.tags().put("jfr.event.type", e.getType().getIdentifier());
		allocPojo.tags().put("jfr.event.name", e.getType().getName());
		allocPojo.timestamp(timestamp(e));
		IMCThread thread = thread(e);
		if (thread != null) {
			allocPojo.counters().set("jfr.threadId", thread.getThreadId());
			allocPojo.tags().put("jfr.threadName", thread.getThreadName());
		}
		IMCStackTrace flrStackTrace = stacktrace(e);
		if (flrStackTrace != null) {
			allocPojo.stackTrace(trace(flrStackTrace));
		}
		else {
			allocPojo.stackTrace(null);
		}
		IMCType type = allocatedType(e);
		if (type != null) {
			allocPojo.tags().put("jfr.allocClass", type.getFullName());
		}
		allocPojo.counters().set("jfr.allocationSize", allocationSize(e));
		allocPojo.counters().set("jfr.tlabSize", newTlabSize(e));
		return allocPojo;
	}
	
	private long timestamp(IItem e) {
		IQuantity qty = E_TIMESTAMP.get(e);
		// TODO unit handling
		return TimeUnit.NANOSECONDS.toMillis(qty.longValue());
	}

	private IMCThread thread(IItem e) {
		IMCThread thread = E_THREAD.get(e);
		return thread;
	}

	private IMCStackTrace stacktrace(IItem e) {
		IMCStackTrace trace = E_STACK_TRACE.get(e);
		return trace;
	}

	private IMCType allocatedType(IItem e) {
		return E_OBJECT_CLASS.get(e);
	}

	private long allocationSize(IItem e) {
		return E_ALLOC_SIZE.get(e).longValue();
	}

	private long newTlabSize(IItem e) {
		return E_NEW_TLAB_SIZE.get(e).longValue();
	}
	
	private StackFrameList trace(IMCStackTrace trace) {
		StackFrame[] t = new StackFrame[trace.getFrames().size()];
		for(int i = 0; i != t.length; ++i) {
			t[i] = frame(trace.getFrames().get(i));
		}
		
		return new StackFrameArray(t);
	}

	private StackFrame frame(IMCFrame f) {
		String pn = f.getMethod().getType().getPackageName();
		String cn = f.getMethod().getType().getTypeName();
		String mn = f.getMethod().getMethodName();
		int line = (f.getFrameLineNumber() == null) ? -1 : f.getFrameLineNumber();
		if (Boolean.TRUE.equals(f.getMethod().isNative())) {
			line = -2;
		}
		String src = null;
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

	private class EventAttr<T> {
		
		private final IAccessorFactory<T> factory;

		private IType<?> lastType;
		private IMemberAccessor<T, IItem> lastKey;
		
		public EventAttr(IAccessorFactory<T> factory) {
			this.factory = factory;
		}

		@SuppressWarnings("unchecked")
		public T get(IItem item) {
			if (item.getType() != lastType) {
				lastKey = (IMemberAccessor<T, IItem>) factory.getAccessor(item.getType());
			}
			return lastKey.getMember(item);
		}
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
