package org.cytoscape.network.merge.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.model.NodeListList;
import org.cytoscape.network.merge.internal.model.NodeSpec;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.ColumnType;

/*
 *	An object responsible for the first pass of merging sets of nodes.
 *
 *
 *	It builds interim lists of nodes that match across networks, and
 *	those that have no counterpart.  These are used to compute union,
 *	intersection and difference sets, as well as a nodeNodeMap that 
 *	maps between original nodes and their equivalent in the target network.
 *
 *	Once the target nodes are determined, a pass is made to copy the
 *  attributes forward.  This can present conflicts and other type
 *  conversion problems.
 *  
 *  The NodeMerger's match function is used by the EdgeMerger in 
 *  the subsequent step.
 */

public class NodeMerger {
	protected final AttributeConflictCollector conflictCollector;
	private NodeListList matchList;
	private List<NodeSpec> unmatchedList;
	private final Map<CyNode, CyNode> nodeNodeMap = new HashMap<CyNode, CyNode>();		// original -> target
	private final Map<Long, HashSet<Long>> nodeMatchMap = new HashMap<Long, HashSet<Long>>();
	private AttributeMap nodeAttributeMap;
	private CyNetwork targetNetwork;
	private NetColumnMap matchingAttribute;
	private AttributeValueMatcher attributeValueMatcher;
	private CyColumn countColumn;
	private CyColumn matchColumn;
	private List<CyNetwork> networks;
	protected boolean withinNetworkMerge = false;

	public List<CyNetwork> getNetworkList() {		return networks;	}

	public NodeMerger(final AttributeConflictCollector conflictCollector) {
		this.conflictCollector = conflictCollector;
	}
	boolean verbose = false;

	public void mergeNodes(List<CyNetwork> sources, 
	                       CyNetwork targetNetwork, 
	                       Operation operation, 
	                       AttributeMap nodeAttribute, 
	                       NetColumnMap matchingAttribute, 
												 AttributeValueMatcher attributeValueMatcher, 
												 CyColumn matchColumn, 
												 CyColumn countColumn) 
	{
		if (verbose) System.out.println("C:  build the list of node list that will merge------------" );

		this.nodeAttributeMap = nodeAttribute;
		this.matchingAttribute = matchingAttribute;
		this.attributeValueMatcher = attributeValueMatcher;
		this.targetNetwork = targetNetwork;
		this.matchColumn = matchColumn;
		this.countColumn = countColumn;
		this.networks = sources;
		matchList = new NodeListList();
		unmatchedList = new ArrayList<NodeSpec>();

		boolean first = false;
		for (CyNetwork net : networks)		// build matchedList and unmatchedList
			if (first && !withinNetworkMerge) {
				first = false;
				for (CyNode node : net.getNodeList())
					unmatchedList.add(new NodeSpec(net, node));
			} else {
				for (CyNode node : net.getNodeList())
					match(new NodeSpec(net, node));
			}

		if (verbose) System.out.println("D:  merge nodes in the matchedList ---------------------------" );
		// if not ALL nets have a node in the match, remove from intersection
		if (operation == Operation.INTERSECTION)
			for (int i = matchList.size()-1; i>=0; i--)
			{
				List<NodeSpec> nodes = matchList.get(i);
				 if (nodes.size() < networks.size())	
					matchList.remove(nodes);
			}

		if (operation == Operation.UNION || operation == Operation.INTERSECTION)		// for Union OR Intersection, add the matches to our target
		{
			for (List<NodeSpec> nodes : matchList)
				addNodeListToTarget(nodes);
		}

		if (operation == Operation.UNION)			//for Union, also add unmatched nodes to target network and nodeNodeMap
		{
			for (NodeSpec node : unmatchedList)
				addNodeToTarget(node);
		}

		if (operation == Operation.DIFFERENCE)			//for difference,  add ONLY nodes from first network that aren't matches
		{
			CyNetwork firstNet = networks.get(0);
			for (CyNode node : firstNet.getNodeList())
			{
				CyNode equiv = nodeNodeMap.get(node);
				boolean matched = equiv != null;
				if (!matched)
					addNodeToTarget(new NodeSpec(firstNet, node));
			}
		}	

		if (verbose) System.out.println("D:  NodeNodeMap has " + nodeNodeMap.size() + " has keys: " + nodeNodeMap.keySet());
		if (verbose) System.out.println("mergedNetwork.size = " + targetNetwork.getNodeCount());

	}

	protected void setWithinNetworkMerge(boolean within) {
		withinNetworkMerge = within;
	}

	//----------------------------------------------------------------
	public void addNodeToTarget(NodeSpec node)
	{
		List<NodeSpec> list = new ArrayList<NodeSpec>();
		list.add(node);
		addNodeListToTarget(list);
	}

	private void addNodeListToTarget(List<NodeSpec> nodes)
	{
		CyNode targetNode = targetNetwork.addNode();
		mergeMatchedNodes(nodes, targetNetwork, targetNode );
		for (NodeSpec node : nodes)
			nodeNodeMap.put(node.getNode(), targetNode);
	}
    
	//----------------------------------------------------------------
	public void mergeMatchedNodes(List<NodeSpec> matchedNodes, CyNetwork targetNetwork, CyNode targetNode) {

		if (matchedNodes == null || matchedNodes.isEmpty())			return;
		final int nattr = nodeAttributeMap.getSizeMergedAttributes();
		CyRow targetRow = targetNetwork.getRow(targetNode);
		CyTable t = targetRow.getTable();
		if (countColumn != null)
			targetRow.set(countColumn.getName(), matchedNodes.size());
		if (matchColumn != null)
		{
			NodeSpec firstNode = matchedNodes.get(0);
			CyNetwork srcNetwork = firstNode.getNet();
			CyColumn col1 = matchingAttribute.get(srcNetwork);
			CyRow srcRow = srcNetwork.getRow(firstNode.getNode());
			String firstVal = srcRow.get(col1.getName(), String.class);
			targetRow.set(matchColumn.getName(), firstVal);
		}

		for (int i = 0; i < nattr; i++) 
		{
			String attribute = nodeAttributeMap.getMergedAttribute(i);
			CyColumn targetColumn = t.getColumn(attribute);

			// build a node to column map
			Map<CyNode, CyColumn> nodeToColMap = new HashMap<CyNode, CyColumn>();
			for (NodeSpec spec : matchedNodes)
			{
				final CyTable table = nodeAttributeMap.getCyTable(spec.getNet());
				if (table == null) continue;

				final String attrName = nodeAttributeMap.getOriginalAttribute(spec.getNet(), i);
				if (attrName == null) continue;
				if (targetNode == null) { System.err.println("null target node");continue; }
				if (targetColumn == null) continue;
				if (attrName.equals(attribute))
					mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);

				final CyColumn column = (table == null) ? null : table.getColumn(attrName);
				for (NodeSpec node : matchedNodes)
					nodeToColMap.put(node.getNode(), column);
			}
			mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);
//			for (CyNetwork net : mapNetToNodes.map.keySet())
//			{
//				boolean isJoinColumn = matchingAttribute.contains(net, attribute);
//				if (isJoinColumn)
//					nodeMerger.mergeAttribute(nodeToColMap, targetNode, matchColumn, countColumn, targetNetwork);
//			}
		}
//		if (Merge.verbose) System.out.println("Node Merged");
	}	
	private void match(final NodeSpec spec) {

		for (List<NodeSpec> matches : matchList) 
		{
			for (NodeSpec node : matches)
				if (matchNode(spec, node))
				{
					matches.add(spec);
					return;
				}
		}
		for (int i = unmatchedList.size()-1; i >= 0; i--)
		{
			NodeSpec unmatched = unmatchedList.get(i);  
			if (matchNode(spec, unmatched))
			{
				List<NodeSpec>  matches = new ArrayList<NodeSpec>();
				matches.add(unmatched);
				matches.add(spec);
				matchList.add(matches);
				unmatchedList.remove(i);
				return;
			}
		}
		unmatchedList.add(spec);
	}

	private boolean matchNode(NodeSpec a, NodeSpec b)
	{
		return matchNode(a.getNet(), a.getNode(), b.getNet(), b.getNode());
	}

	protected boolean matchNodeFast(final CyNode n1, final CyNode n2) {
		Long n1SUID = n1.getSUID();
		Long n2SUID = n2.getSUID();
		if (n1SUID.equals(n2SUID)) return true;
		if ((nodeMatchMap.containsKey(n1SUID) && nodeMatchMap.get(n1SUID).contains(n2SUID)) || 
		    (nodeMatchMap.containsKey(n2SUID) && nodeMatchMap.get(n2SUID).contains(n1SUID)))
			return true;
		return false;
	}

	protected boolean matchNode(final CyNetwork net1, final CyNode n1, final CyNetwork net2, final CyNode n2) {
		if (net1 == null || n1 == null || net2 == null || n2 == null)
			throw new NullPointerException();

		// it matches if the same node is sent twice, but they are nodes in different networks, so we shouldn't see this case
		if (n1 == n2)   return true;

		CyColumn col1 = matchingAttribute.get(net1);
		CyColumn col2 = matchingAttribute.get(net2);

		if (col1 == null || col2 == null)
			throw new IllegalArgumentException("Please specify the matching table column first");

		boolean result = attributeValueMatcher.matched(n1, col1, n2, col2);
//		if (result && verbose)
//			System.out.println((result? "MATCH " : "NOMATCH   ") + nodeName(net1, n1) + " " + net1.getSUID() + ": " + col1.getName() 
//				+ ", "  + nodeName(net2, n2) + " " + net2.getSUID() + ": " + col2.getName());
		if (result) {
			// Save this match for later
			Long n1SUID = n1.getSUID();
			Long n2SUID = n2.getSUID();
			if (!nodeMatchMap.containsKey(n1SUID))
				nodeMatchMap.put(n1SUID, new HashSet<Long>());
			if (!nodeMatchMap.containsKey(n2SUID))
				nodeMatchMap.put(n2SUID, new HashSet<Long>());
			nodeMatchMap.get(n1SUID).add(n2SUID);
			nodeMatchMap.get(n2SUID).add(n1SUID);
		}
		return result; 
	}

	//===============================================================================================
	// NB: source and target refer to the nodes in the merge, not edge source or edge target

	public void mergeAttribute(Map<CyNode, CyColumn> nodeColMap, CyNode targetNode, CyColumn targetColumn, CyColumn countColumn, CyNetwork targetNet) 
	{

//		if (Merge.verbose) System.out.println("mergeAttribute " + (node == null ? "NULLNODE" : node.getSUID()) + " " + (targetColumn == null ? "NULLTARGET" : targetColumn.getName()));
		if (nodeColMap == null) 	return;
		if (targetNode == null) 			return;
		if (targetColumn == null) 	return;
		if (targetNet == null) 		return;


//		if (Merge.verbose) System.out.println("NodeMerger.mergeAttribute: " + targetColumn.getName());

		for (CyNode source : nodeColMap.keySet()) {
//			Merge.dumpRow(source);
			final CyColumn sourceColumn = nodeColMap.get(source);
//			if (Merge.verbose) System.out.println("merge: " + fromColumn.getName() + " " + node.getSUID() + " " + targetNet.getSUID() + " " + targetColumn.getName());
			merge(source, sourceColumn, targetNet, targetNode, targetColumn, countColumn);
		}
	}

	/*
	 * merge 
	 * 
	 * we want to copy from source to target within one cell of the table
	 * if target is empty, put source into it
	 * if they are the same value, do nothing
	 * if the target is a list, add the value to the list [set]
	 * if there is a conflict, stash it away for later processing
	 */
	private void merge(CyNode sourceNode, CyColumn sourceColumn, CyNetwork targetNet, CyNode target, CyColumn targetColumn, CyColumn countColumn) {

		if (sourceNode == null) 			return;
		if (target == null) 				return;
//		if (sourceNode == sourceColumn) 	return;		// ??
//		if (target == targetColumn) 		return;				// ??

		final ColumnType targColType = ColumnType.getType(targetColumn);
		final ColumnType sourceColType = ColumnType.getType(sourceColumn);
		final CyRow targetRow = targetNet.getRow(target);
		
		final CyTable sourceTable = sourceColumn.getTable();
		final CyRow sourceRow = sourceTable.getRow(sourceNode.getSUID());

		if (targetRow == null) throw new NullPointerException("targetRow");
		if (sourceRow == null) throw new NullPointerException("fromCyRow");

if (verbose)
	System.out.println(sourceNode.getSUID() + " " + sourceColumn.getName() + " " + sourceColType);
	

		if (sourceColType == ColumnType.STRING) 
		{
			String s = sourceColumn.getName();
			try
			{
				
				final Object sourceValue = sourceRow.get(s, String.class);
				final Object extant = targColType.isList() ? 
				targetRow.getList(targetColumn.getName(), targColType.getType())
		 : targetRow.get(targetColumn.getName(), targColType.getType());
				
				if (sourceValue == null) return;
				if (extant == null)  // null or empty attribute   || o2.length() == 0
				{
					targetRow.set(targetColumn.getName(), sourceValue);						
					if (countColumn != null) targetRow.set(countColumn.getName(), 1);
					return;
				}
				
				if (sourceValue.equals(extant))  return;	 // the same value , do nothing
				
				conflictCollector.addConflict(sourceNode, sourceColumn, target, targetColumn);
			}
			catch (IllegalArgumentException ex)
			{
				if (verbose) 
					System.out.println("IllegalArgumentException: " + ex.getMessage() + " in merge");
			}
		return;
		
	} 
	
	
	
	if (!sourceColType.isList() && !targColType.isList()) 			// simple type: (Integer, Long, Double, Boolean)
	{ 
		Object sourceValue = sourceRow.get(sourceColumn.getName(), sourceColType.getType());
		if (sourceValue == null) 
		{
			System.err.println("missing value: " + sourceColumn.getName() + " " + sourceColType.getType());
			return;
		}
			
		if (!sourceColType.equals(targColType))
				sourceValue = targColType.castService(sourceValue);
		final Object extant = targetRow.get(targetColumn.getName(), targColType.getType());
		if (extant == null) 
		{
			targetRow.set(targetColumn.getName(), sourceValue);		
			if (countColumn != null) targetRow.set(countColumn.getName(), 2);			
//				System.out.println("mergeAttribute: " + o1);
			return;
		}
			
		if (sourceValue.equals(extant)) return;			// TODO -- does this work for Doubles
			
		conflictCollector.addConflict(sourceNode, sourceColumn, target, targetColumn);

		return;
		
	} 
	
	if (targColType.isList())	// targColType is a list
	{
			// TODO: use a conflict handler to handle this part?
	ColumnType plainType = targColType.toPlain();

	List l2 = targetRow.getList(targetColumn.getName(), targColType.getType());
	if (l2 == null) {
				l2 = new ArrayList<Object>();
			}

	if (!sourceColType.isList()) {
				// Simple data type
		Object o1 = sourceRow.get(sourceColumn.getName(), sourceColType.getType());
		if (o1 != null) {
			if (plainType != sourceColType) 
						o1 = plainType.castService(o1);
					if (!l2.contains(o1)) 
						l2.add(o1);
				if (!l2.isEmpty()) 
				{
					targetRow.set(targetColumn.getName(), l2);		// <-------
					if (countColumn != null) targetRow.set(countColumn.getName(), l2.size());		// <-------
				}
				}
			} else { // from list
				final ColumnType fromPlain = sourceColType.toPlain();
				final List<?> list = sourceRow.getList(sourceColumn.getName(), fromPlain.getType());
				if(list == null)				return;

				for (final Object listValue:list) {
					if(listValue == null)		continue;

					final Object validValue;
					if (plainType != sourceColType.toPlain()) 
						validValue = plainType.castService(listValue);
					else
						validValue = listValue;
					if (!l2.contains(validValue)) 
						l2.add(validValue);
				}
			}

			if(!l2.isEmpty()) 
			{
				targetRow.set(targetColumn.getName(), l2);		// <-------
				if (countColumn != null) targetRow.set(countColumn.getName(), l2.size());		// <-------
			}
		}
	}

	public String nodeName(CyNetwork net, CyNode target) {
		return NetworkMergeCommandTask.getNodeName(net, target);
	}

	public CyNode targetLookup(CyNode source) {
		return nodeNodeMap.get(source);
	}
}

