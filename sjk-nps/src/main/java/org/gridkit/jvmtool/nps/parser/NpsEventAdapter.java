package org.gridkit.jvmtool.nps.parser;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.gridkit.jvmtool.codec.stacktrace.ThreadSnapshotEventPojo;
import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventMorpher;
import org.gridkit.jvmtool.event.EventReader;
import org.gridkit.jvmtool.event.MorphingEventReader;
import org.gridkit.jvmtool.nps.parser.NpsDumpTree.Node;
import org.gridkit.jvmtool.stacktrace.StackFrame;
import org.gridkit.jvmtool.stacktrace.StackFrameArray;
import org.netbeans.lib.profiler.results.CCTNode;
import org.netbeans.lib.profiler.results.cpu.CPUResultsSnapshot;
import org.netbeans.lib.profiler.results.cpu.PrestimeCPUCCTNode;

public class NpsEventAdapter implements EventReader<Event> {

	private final CPUResultsSnapshot snapshot;
	private final NpsDumpTree tree;
	private long begingTime;
	private Iterator<Node> nextNode;
	private Node lastNode = null;
	private int multiplier = 0;
	private ThreadSnapshotEventPojo nextEvent;

	public NpsEventAdapter(CPUResultsSnapshot snapshot) {
		this.snapshot = snapshot;
		this.tree = new NpsDumpTree(snapshot);
		this.begingTime = snapshot.getBeginTime();
		this.nextNode = tree.globalList.iterator();
		seek();
	}

	private void seek() {
		nextEvent = new ThreadSnapshotEventPojo();
		if (!next(nextEvent)) {
			nextEvent = null;
		}
	}

	@Override
	public Iterator<Event> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		return nextEvent != null;
	}

	@Override
	public Event next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		Event e = nextEvent;
		seek();
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
		return nextEvent;
	}

	@Override
	public void dispose() {
		// do nothing
	}

	public boolean next(ThreadSnapshotEventPojo pojo) {
		if (multiplier <= 0) {
			while (nextNode.hasNext()) {
				lastNode = nextNode.next();
				multiplier = (int) lastNode.vsampleSelfCount;
				if (lastNode.vsampleSelfCount > 0) {
					break;
				}
			}

			if (multiplier <= 0) {
				return false;
			}
		}
		pojo.stackTrace(new StackFrameArray(lastNode.path));
		pojo.threadId(lastNode.threadId);
		pojo.threadName(snapshot.getThreadNameForId(lastNode.threadId));
		pojo.timestamp(begingTime);
		--multiplier;

		return true;
	}
}
