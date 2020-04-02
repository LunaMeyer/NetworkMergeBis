package org.cytoscape.network.merge.internal.ui;

/*
 * #%L
 * Cytoscape Merge Impl (network-merge-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.util.ColumnType;
import org.cytoscape.util.swing.LookAndFeelUtil;

/**
 * Table for customizing attribute mapping from original networks to resulting network
 */
@SuppressWarnings("serial")
class MergeAttributeTable extends JTable {

	private final String nullAttr = "[DELETE THIS]";
	private NetColumnMap matchingAttribute;
	private AttributeMap attributeMapping; // attribute mapping
	private String mergedNetworkName;
	private MergeAttributeTableModel model;
	private boolean isNode;
	private int indexMatchingAttr; // the index of matching attribute in the attribute mapping
								   // only used when isNode==true

	public MergeAttributeTable(final AttributeMap mapping, final NetColumnMap attribute) {
		super();
		isNode = true;
		indexMatchingAttr = -1;
		mergedNetworkName = "Merged Network";
		attributeMapping = mapping;
		matchingAttribute = attribute;
		model = new MergeAttributeTableModel();
		setModel(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}

	public MergeAttributeTable(final AttributeMap attribute) {
		super();
		mergedNetworkName = "Merged Network";
		attributeMapping = attribute;
		model = new MergeAttributeTableModel();
		isNode = false;
		setModel(model);
	}

	public String getMergedNetworkName() {		return mergedNetworkName;	}

	private Vector<String> getComboboxOption(int col) {
		CyNetwork net = model.getNetork(col);
		CyTable table = attributeMapping.getCyTable(net);
		Vector<String> colNames = new Vector<String>();
		for (CyColumn cyCol : table.getColumns()) {
			String colName = cyCol.getName();
			if (!colName.equals("SUID") && !colName.equals("selected")) {
				colNames.add(colName);
			}
		}
		colNames.add(nullAttr);
		return colNames;
	}

	protected void setColumnEditorAndRenderer() {
		final int n = getColumnCount();
		
		for (int i = 0; i < n; i++) { // for each network
			final TableColumn column = getColumnModel().getColumn(i);

			if (isColumnOriginalNetwork(i)) {
				final Vector<String> attrs = getComboboxOption(i);
				final JComboBox<String> comboBox = new JComboBox<>(attrs);
				column.setCellEditor(new DefaultCellEditor(comboBox));
				
				column.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
							boolean hasFocus, int row, int column) {
						super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						
						setForeground(UIManager.getColor(isSelected ? "Table.selectionForeground" : "Table.foreground"));
						setBackground(UIManager.getColor(isSelected ? "Table.selectionBackground" : "Table.background"));
						
						if (row < (isNode ? 1 : 0)) // TODO Cytoscape3
							setToolTipText("Change this in the matching node table above");
						else
							setToolTipText(null);
						
						return this;
					}
				});
			} else if (isColumnMergedNetwork(i)) {
				column.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
							boolean hasFocus, int row, int column) {
						super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						
						if (isNode && row == 0)
							setToolTipText("Change me!");
						else
							setToolTipText("Click to change...");
						
						if (isSelected) {
							setForeground(UIManager.getColor("Table.selectionForeground"));
							setBackground(UIManager.getColor("Table.selectionBackground"));
						} else {
							setBackground(UIManager.getColor("Table.background"));
							
							if (row >= table.getRowCount() - 1) {
								setForeground(UIManager.getColor("TextField.inactiveForeground"));
							} else {
								if (isNode && row == 0) {
									setForeground(LookAndFeelUtil.getErrorColor());
									setToolTipText("Change me!");
								} else {
									setForeground(table.getForeground());
									setToolTipText("Click to change...");
								}
							}
						}
						
						return this;
					}
				});
			} else if (isColumnMergedType(i)) {
				// set editor
				RowTableCellEditor rowEditor = new RowTableCellEditor(this);
				int nr = getRowCount();
				final Vector<ColumnType>[] cbvalues = new Vector[nr];
				
				for (int ir = 0; ir < nr - 1; ir++) {
					int iAttr = ir;

					Map<CyNetwork, String> mapNetAttr = attributeMapping.getOriginalAttributeMap(iAttr);
					Set<ColumnType> types = EnumSet.noneOf(ColumnType.class);
					
					for (Map.Entry<CyNetwork, String> entry : mapNetAttr.entrySet()) {
						CyTable cyTable = attributeMapping.getCyTable(entry.getKey());
						types.add(ColumnType.getType(cyTable.getColumn(entry.getValue())));
					}
					
					ColumnType reasonalbeType = ColumnType.getReasonableCompatibleConversionType(types);
					Vector<ColumnType> convertiableTypes = new Vector<>(ColumnType.getConvertibleTypes(reasonalbeType));

					cbvalues[ir] = convertiableTypes;

					JComboBox<ColumnType> cb = new JComboBox<>(convertiableTypes);
					cb.setSelectedItem(attributeMapping.getMergedAttributeType(iAttr));

					rowEditor.setEditorAt(ir, new DefaultCellEditor(cb));
				}
				
				column.setCellEditor(rowEditor);

				// set renderer
				column.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
							boolean hasFocus, int row, int column) {
						super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						
						setForeground(UIManager.getColor(isSelected ? "Table.selectionForeground" : "Table.foreground"));
						setBackground(UIManager.getColor(isSelected ? "Table.selectionBackground" : "Table.background"));
						
						if (row >= table.getRowCount() - 1) {
							setBackground(UIManager.getColor("TextField.inactiveForeground"));
						} else if (!table.isCellEditable(row, column)) {
							setBackground(UIManager.getColor("TextField.inactiveForeground"));
							setToolTipText("Only types of new columns are changeable");
						}
						
						return this;
					}
				});
			}
		}
	}

	protected void setMergedNetworkNameTableHeaderListener() {
		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				JTableHeader header = (JTableHeader) e.getSource();
				JTable table = header.getTable();
				int columnIndex = header.columnAtPoint(e.getPoint());
				if (columnIndex == attributeMapping.size()) { // the merged network
					String title = JOptionPane.showInputDialog(table.getParent(),
							"Input the title for the merged network");
					if (title != null && title.length() != 0) {
						mergedNetworkName = title;
						fireTableHeaderChanged();
					} else {
						// TODO: this is just to remove the duplicate click event; there should be better ways
						fireTableHeaderChanged();
					}
				}
			}
		});
	}

	public void fireTableStructureChanged() {
		model.fireTableStructureChanged();
		setColumnEditorAndRenderer();
		setMergedNetworkNameTableHeaderListener();
	}

	public void updateMatchingAttribute() {
		if (!isNode) {
			throw new java.lang.UnsupportedOperationException(
					"updateMatchingAttribute is only supported for node table");
		}

		boolean update = false;
		if (indexMatchingAttr == -1) {
			indexMatchingAttr = 0;
			String attr_merged = "Matching.Attribute";

			Map<CyNetwork, String> netAttrMap = new HashMap<CyNetwork, String>();
			Set<ColumnType> types = EnumSet.noneOf(ColumnType.class);
			for (CyNetwork net : matchingAttribute.getNetColumnMap().keySet()) {
				CyColumn c = matchingAttribute.getNetColumnMap().get(net);
				netAttrMap.put(net, c.getName());
				types.add(ColumnType.getType(c).toPlain());
			}

			attributeMapping.addAttributes(netAttrMap, attr_merged, indexMatchingAttr);
			attributeMapping.setMergedAttributeType(indexMatchingAttr,
					ColumnType.getReasonableCompatibleConversionType(types));  //.toList()
			update = true;
		} else {
			List<CyNetwork> networks = matchingAttribute.getNetworkList();
			if (networks.isEmpty())  // empty
				indexMatchingAttr = -1;

			for (CyNetwork network : networks) {
				String attr = matchingAttribute.get(network).getName();
				String old = attributeMapping.setOriginalAttribute(network, attr, indexMatchingAttr);
				if (attr.compareTo(old) != 0) 
					update = true;
			}
		}

		if (update) 
			fireTableStructureChanged();
	}

	protected void fireTableHeaderChanged() {
		model.fireTableStructureChanged();
		setColumnEditorAndRenderer();
	}

	protected boolean isColumnOriginalNetwork(final int col) {
		return col >= 0 && col < attributeMapping.size();
	}

	protected boolean isColumnMergedNetwork(final int col) {
		return col == attributeMapping.size();
	}

	protected boolean isColumnMergedType(final int col) {
		return col == attributeMapping.size() + 1;
	}

	// table model
	protected class MergeAttributeTableModel extends AbstractTableModel {
		ArrayList<CyNetwork> networks;

		public MergeAttributeTableModel() {
			resetNetworks();
		}

		// @Override
		public int getColumnCount() {
			final int n = attributeMapping.size();
			return n == 0 ? 0 : n + 2;
		}

		// @Override
		public int getRowCount() {
			int n = attributeMapping.getSizeMergedAttributes() + 1; // +1: add an empty row in the end
			return attributeMapping.size() == 0 ? 0 : n;
		}

		@Override
		public String getColumnName(final int col) {
			if (isColumnMergedType(col))			return "Column type";
			if (isColumnMergedNetwork(col)) 		return mergedNetworkName;
			if (isColumnOriginalNetwork(col)) 		return networks.get(col).toString();
			return null;
		}

		@Override
		public Object getValueAt(final int row, final int col) {
			final int iAttr = row; 

			if (row == getRowCount() - 1) 				return null;
			if (isColumnOriginalNetwork(col)) 			return attributeMapping.getOriginalAttribute(networks.get(col), iAttr);
			if (isColumnMergedNetwork(col)) 			return attributeMapping.getMergedAttribute(iAttr);
			if (isColumnMergedType(col)) 				return attributeMapping.getMergedAttributeType(iAttr);
			return null;
		}

		@Override
		public Class getColumnClass(final int c) {
			return String.class;
		}

		@Override
		public boolean isCellEditable(final int row, final int col) {
			if (isNode && row == 0) // make the matching attribute ineditable
				return !isColumnOriginalNetwork(col);

			if (isColumnOriginalNetwork(col))			return true;
			if (isColumnMergedNetwork(col))				return row != getRowCount() - 1;
			if (isColumnMergedType(col))				return true;
			return false;
		}

		@Override
		public void setValueAt(final Object value, final int row, final int col) {
			if (value == null)
				return;

			final int iAttr = row; // TODO use in Cytoscape3.0

			final int n = attributeMapping.getSizeMergedAttributes();
			if (iAttr > n)
				return; // should not happen

			if (isColumnMergedType(col)) {
				if (iAttr == n)					return;

				ColumnType type = (ColumnType) value;
				ColumnType type_curr = attributeMapping.getMergedAttributeType(iAttr);
				if (type == type_curr)			return;

				attributeMapping.setMergedAttributeType(iAttr, type);

			} else if (isColumnMergedNetwork(col)) { // column of merged network
				if (iAttr == n)					return;

				final String v = (String) value;

				String attr_curr = attributeMapping.getMergedAttribute(iAttr);
				if (attr_curr.compareTo(v) == 0)	return; // if the same
				

				if (v.length() == 0) {
					JOptionPane.showMessageDialog(getParent(), "Please use a non-empty name for the column.",
							"Error: empty column name", JOptionPane.ERROR_MESSAGE);
					return;
				}

				if (attributeMapping.containsMergedAttribute(v)) {
					JOptionPane.showMessageDialog(getParent(), "Column " + v
							+ " already exist. Please use another name for this column.",
							"Error: duplicated column name", JOptionPane.ERROR_MESSAGE);
					return;
				}

				attributeMapping.setMergedAttribute(iAttr, v);
				fireTableDataChanged();
				return;
			} else { // column of original network
				CyNetwork net = networks.get(col);
				final String v = (String) value;
				if (iAttr == n) { // the last row
					if (v.compareTo(nullAttr) == 0)
						return;

					Map<CyNetwork, String> map = new HashMap<CyNetwork, String>();
					map.put(net, v);
//System.out.println(v.toString());
					attributeMapping.addAttributes(map, v);
					fireTableDataChanged();
					return;

				} else {
					String curr_attr = attributeMapping.getOriginalAttribute(net, iAttr);
					if (curr_attr != null && curr_attr.compareTo(v) == 0) {
						return;
					}

					if (v.compareTo(nullAttr) == 0) {
						if (curr_attr == null)
							return;
						// if (attributeMapping.getOriginalAttribute(netID,
						// iAttr)==null) return;
						attributeMapping.removeOriginalAttribute(net, iAttr);
					} else {
						attributeMapping.setOriginalAttribute(net, v, iAttr);// set
																				// the
																				// v
					}
					fireTableDataChanged();
					return;
				}
			}
		}

		@Override
		public void fireTableStructureChanged() {
			resetNetworks();
			super.fireTableStructureChanged();
		}

		private void resetNetworks() {
			networks = new ArrayList<CyNetwork>(attributeMapping.getNetworkSet());
			// TODO: sort networks maybe alphabetically
		}

		public CyNetwork getNetork(int col) {
			return networks.get(col);
		}
	}

}
