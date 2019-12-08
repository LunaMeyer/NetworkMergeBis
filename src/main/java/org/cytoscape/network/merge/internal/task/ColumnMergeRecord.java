package org.cytoscape.network.merge.internal.task;

import java.util.HashMap;
import java.util.List;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.network.merge.internal.util.ColumnType;

//---------------------------------------------------------------------
public class ColumnMergeRecord 
{
	HashMap<CyNetwork, String> columnNames = new HashMap<CyNetwork, String>();
	public String outName;
	public ColumnType outType;
	
	ColumnMergeRecord(String str, List<CyNetwork> netw)
	{
		String[] names = str.split(",");
		int size = names.length;
		if (size != netw.size() + 2)
			throw new IllegalArgumentException("Wrong number of arguments, given size of network list");
		for (int i=0; i<size-2; i++)
			columnNames.put(netw.get(i), names[i]);
		outName = names[size-2];
		outType = ColumnType.STRING;
		
		System.out.println();
		
	}
	
	void dump()
	{
		System.out.println(outName + " # " + outType.toString());
		for (CyNetwork net : columnNames.keySet())
			System.out.println(NetworkMergeCommandTask.getNetworkName(net) + ": " + columnNames.get(net));
		System.out.println("\n");
	}
}
