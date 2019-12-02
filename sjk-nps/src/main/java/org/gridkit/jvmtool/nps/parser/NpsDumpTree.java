package org.gridkit.jvmtool.nps.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gridkit.jvmtool.stacktrace.StackFrame;
import org.netbeans.lib.profiler.results.CCTNode;
import org.netbeans.lib.profiler.results.cpu.CPUResultsSnapshot;
import org.netbeans.lib.profiler.results.cpu.PrestimeCPUCCTNode;

class NpsDumpTree {

	private final CPUResultsSnapshot snapshot;
	private StackFrame[] frameCache = new StackFrame[256];
	private long gcd = 0;

	List<Node> globalList = new ArrayList<Node>();

	public NpsDumpTree(CPUResultsSnapshot snapshot) {
		this.snapshot = snapshot;
		PrestimeCPUCCTNode root = snapshot.getRootNode(CPUResultsSnapshot.METHOD_LEVEL_VIEW);
		makeTree(null, root);
		initSelfTime();
		quantize();
	}

	private void initSelfTime() {
		for (Node n: globalList) {
			long tt = n.time;
			for (Node cn: n.children) {
				tt -= cn.time;
			}
			n.selfTime = tt;
			gcd = gcd(gcd, tt);
		}
	}

	private void quantize() {
		for (Node n: globalList) {
			n.vsampleSelfCount = n.selfTime / gcd;
		}
	}

	private long gcd(long a, long b) {
		if (a == 0) {
			return b;
		}
		if (b == 0) {
			return a;
		}
		if (a > b) {
			return gcd(b, a % b);
		}
		else {
			return gcd(a, b % a);
		}
	}

	private void makeTree(Node parent, PrestimeCPUCCTNode node) {
		if (node.isSelfTimeNode()) {
			return;
		}
		StackFrame frame = frame(node.getMethodId());
		if (frame == null) {
			for (CCTNode cnode: node.getChildren()) {
				makeTree(null, (PrestimeCPUCCTNode) cnode);
			}
		} else {
			Node tnode;
			if (parent == null) {
				tnode = new Node(frame);
				tnode.threadId = ((PrestimeCPUCCTNode)node.getParent()).getThreadId();
			} else {
				tnode = new Node(parent, frame);
			}
			globalList.add(tnode);
			tnode.time = (long) ((256 << 10) * (0.01 * node.getTotalTime0InPerCent()));
			if (node.getChildren() != null) {
				for (CCTNode cnode: node.getChildren()) {
					makeTree(tnode, (PrestimeCPUCCTNode) cnode);
				}
			}
		}
	}

	private StackFrame frame(int id) {
		if (id == 0) {
			return null;
		}
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

	static class Node {

		StackFrame[] path;
		int threadId;
		long time;
		long selfTime;
		long vsampleSelfCount;

		List<Node> children = new ArrayList<Node>();

		public Node(StackFrame path) {
			this.path = new StackFrame[] {path};
		}

		public Node(Node parent, StackFrame path) {
			this.path = new StackFrame[parent.path.length + 1];
			this.path[0] = path;
			System.arraycopy(parent.path, 0, this.path, 1, parent.path.length);
			this.threadId = parent.threadId;
			parent.children.add(this);
		}

	}
}
