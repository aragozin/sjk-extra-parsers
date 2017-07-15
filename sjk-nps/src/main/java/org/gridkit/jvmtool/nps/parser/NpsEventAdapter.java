package org.gridkit.jvmtool.nps.parser;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.gridkit.jvmtool.codec.stacktrace.ThreadSnapshotEventPojo;
import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventMorpher;
import org.gridkit.jvmtool.event.EventReader;
import org.gridkit.jvmtool.event.MorphingEventReader;
import org.gridkit.jvmtool.stacktrace.StackFrame;
import org.gridkit.jvmtool.stacktrace.StackFrameArray;
import org.netbeans.lib.profiler.results.CCTNode;
import org.netbeans.lib.profiler.results.cpu.CPUResultsSnapshot;
import org.netbeans.lib.profiler.results.cpu.PrestimeCPUCCTNode;

public class NpsEventAdapter implements EventReader<Event> {

	private final CPUResultsSnapshot snapshot;
	private StackFrame[] frameCache = new StackFrame[256];
	@SuppressWarnings("unused")
	private TreeWalkNode root;
	private TreeWalkNode lastNode;
	private long begingTime;
	private ThreadSnapshotEventPojo nextEvent;

	public NpsEventAdapter(CPUResultsSnapshot snapshot) {
		this.snapshot = snapshot;
		this.begingTime = snapshot.getBeginTime();
		init();
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

	private void init() {
		PrestimeCPUCCTNode root = snapshot.getRootNode(CPUResultsSnapshot.METHOD_LEVEL_VIEW);
		this.root = new TreeWalkNode(null, root);		
	}
	
	public boolean next(ThreadSnapshotEventPojo pojo) {
		if (!nextEvent()) {
			return false;
		}
		pojo.stackTrace(new StackFrameArray(lastNode.trace));
		pojo.threadId(lastNode.threadId);
		pojo.threadName(snapshot.getThreadNameForId(lastNode.threadId));
		pojo.timestamp(begingTime);
		return true;
	}

	private boolean nextEvent() {
		if (lastNode.dupRemaining > 0) {
			--lastNode.dupRemaining;
			return true;
		}
		else {
			TreeWalkNode c = lastNode.parent;
			while(c != null) {
				if (c.initNextChild()) {
					return true;
				}
				else {
					c = c.parent;
				}
			}
			return false;
		}
	}

	private StackFrame frame(int id) {
		if (frameCache.length <= id) {
			frameCache = Arrays.copyOf(frameCache, Math.max(2 * frameCache.length, id + 1));
		}
		if (frameCache[id] == null) {
			String[] mi = snapshot.getMethodClassNameAndSig(id, CPUResultsSnapshot.METHOD_LEVEL_VIEW);
			String cn = mi[0];
			String m = mi[1];
			boolean ntv = false;
			if (m.endsWith("[native]")) {
				ntv = true;
				m = m.substring(0, m.length() - "[native]".length());
			}
			StackFrame sf = new StackFrame("", cn, m, null, ntv ? - 2 : -1);
			frameCache[id] = sf;
		}
		return frameCache[id];
	}

	private class TreeWalkNode {
		
		private TreeWalkNode parent;
		private int threadId;
		private PrestimeCPUCCTNode srcNode;
		private int frameDepth;
		private int frameRef; 
		private int nextChild;
		private int dupRemaining;
		
		private StackFrame[] trace;
		
		public TreeWalkNode(TreeWalkNode parent, PrestimeCPUCCTNode node) {
			this.parent = parent;
			this.threadId = 0;
			this.srcNode = node;
			this.frameDepth = 0;
			this.frameRef = 0;
			this.nextChild = 0;
			this.dupRemaining = 0;
			
			if (parent != null) {
				if (parent.parent == null) {
					this.threadId = node.getThreadId();
				}
				else {
					this.threadId = parent.threadId;
					this.frameRef = node.getMethodId();
					if (frameRef != 0) {
						this.frameDepth = parent.frameDepth + 1;
					}
				}
			}

			lastNode = this;
			
			if (!initNextChild()) {
				// terminal node
				this.dupRemaining = node.getNCalls();
				initTrace();
			}
		}

		protected boolean initNextChild() {
			while(nextChild < srcNode.getNChildren()) {
				CCTNode node = srcNode.getChild(nextChild);
				nextChild++;
				if (node instanceof PrestimeCPUCCTNode) {
					PrestimeCPUCCTNode cnode = (PrestimeCPUCCTNode) node;
					if (cnode.isSelfTimeNode()) {
						continue;
					}
					new TreeWalkNode(this, cnode);
					return true;
				}
			}
			return false;
		}
		
		private void initTrace() {
			trace = new StackFrame[frameDepth];
			int n = 0;
			TreeWalkNode tn = this;
			while(tn != null) {
				if (tn.frameRef != 0) {
					trace[n++] = frame(tn.frameRef);
				}
				tn = tn.parent;
			}			
		}
	}
}
