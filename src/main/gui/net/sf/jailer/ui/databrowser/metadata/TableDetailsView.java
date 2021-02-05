/*
 * Copyright 2007 - 2021 Ralf Wisser.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.databrowser.metadata;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sf.jailer.datamodel.Column;
import net.sf.jailer.datamodel.DataModel;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.modelbuilder.ModelBuilder;
import net.sf.jailer.ui.JComboBox2;
import net.sf.jailer.ui.StringSearchPanel;
import net.sf.jailer.ui.UIUtil;
import net.sf.jailer.ui.databrowser.Row;
import net.sf.jailer.util.Quoting;

/**
 * Table Details View.
 *
 * @author Ralf Wisser
 */
@SuppressWarnings("serial")
public class TableDetailsView extends javax.swing.JPanel {

	private final Color selectedBG = new Color(255, 200, 200);
	private final Runnable updateColumnsTable;
	private boolean cacheable = true;
	private Map<String, JComponent> rows = new HashMap<String, JComponent>();
	
	/**
     * Creates new form TableDetailsView
	 * @param row 
     */
    public TableDetailsView(final Table table, final MDTable mdTable, final MetaDataDetailsPanel metaDataDetailsPanel, final Row row, final DataModel dataModel, TableDetailsView currentView) {
        initComponents();
        if (jScrollPane1.getHorizontalScrollBar() != null) {
        	jScrollPane1.getHorizontalScrollBar().setUnitIncrement(16);
        }
        if (jScrollPane1.getVerticalScrollBar() != null) {
        	jScrollPane1.getVerticalScrollBar().setUnitIncrement(16);
        }
        if (currentView != null) {
        	sortColumnsCheckBox.setSelected(currentView.sortColumnsCheckBox.isSelected());
        	foundColumn = currentView.foundColumn;
        }
		findColumnsLabel.setText(null);
		findColumnsLabel.setToolTipText("Find Column...");
		findColumnsLabel.setIcon(StringSearchPanel.getSearchIcon(false, this));

		findColumnsLabel.addMouseListener(new java.awt.event.MouseAdapter() {
			private boolean in = false;

			@Override
			public void mousePressed(MouseEvent e) {
				if (findColumnsLabel.isEnabled()) {
					UIUtil.invokeLater(new Runnable() {
						@Override
						public void run() {
							in = false;
							updateBorder();
							if (findColumnsPanel.isShowing()) {
								Point point = new Point();
								SwingUtilities.convertPointToScreen(point, findColumnsPanel);
								findColumns((int) point.getX(), (int) point.getY());
							}
						}
					});
				}
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent evt) {
				if (findColumnsLabel.isEnabled()) {
					in = true;
					updateBorder();
				}
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent evt) {
				in = false;
				updateBorder();
			}

			private void updateBorder() {
				findColumnsPanel.setBackground(in? new Color(240, 240, 0) : null);
				findColumnsPanel.setOpaque(in);
			}
		});
		updateColumnsTable = new Runnable() {
        	final Font font = new JLabel().getFont();
			final Font nonbold = new Font(font.getName(), font.getStyle() & ~Font.BOLD, font.getSize());
			final Font italic = new Font(nonbold.getName(), nonbold.getStyle() | Font.ITALIC, nonbold.getSize());
			
			@Override
			public void run() {
		        String typeLabel = "";
		        if (mdTable != null) {
		        	if (mdTable.isView()) {
		        		typeLabel = "View ";
		        	} else if (mdTable.isSynonym()) {
		        		typeLabel = "Synonym ";
		        	}
		        }
				tableNameLabel.setText(typeLabel + dataModel.getDisplayName(table));
		        Font font = new JLabel("L").getFont();
				tableNameLabel.setFont(new Font(font.getName(), font.getStyle(), (int)(font.getSize() * 1.2)));
		
				boolean mdTableIsUpTodate = true;
				if (mdTable != null) {
					if (!mdTable.isLoaded()) {
						cacheable = false;
					} else {
						mdTableIsUpTodate = mdTable.isUptodate(table);
					}
				} else {
					cacheable = false;
				}
				if (mdTable != null && !mdTableIsUpTodate && !ModelBuilder.isJailerTable(table.getUnqualifiedName())) {
					warnLabel.setIcon(MetaDataPanel.getScaledIcon(TableDetailsView.this, MetaDataPanel.warnIcon, false));
					analyseButton.setText("Analyse schema \"" + mdTable.getSchema().getUnquotedName() + "\"");
					analyseButton.addActionListener(new ActionListener() {
		                @Override
		                public void actionPerformed(ActionEvent e) {
		                	metaDataDetailsPanel.analyseSchema(mdTable.getSchema().getName(), mdTable.isView(), mdTable.isSynonym());
		                }
					});
				} else {
					warnPanel.setVisible(false);
				}
		
				int y = 1;
				List<Column> columns = table.getColumns();
				Map<Column, Object> columnValue = new IdentityHashMap<Column, Object>();
				for (int i = 0; i < columns.size(); ++i) {
					if (row != null && row.values.length > i) {
						columnValue.put(columns.get(i), row.values[i]);
					}
				}
				if (sortColumnsCheckBox.isSelected()) {
					columns = new ArrayList<Column>(columns);
					Collections.sort(columns, new Comparator<Column>() {
						@Override
						public int compare(Column o1, Column o2) {
							return Quoting.staticUnquote(o1.name).compareToIgnoreCase(Quoting.staticUnquote(o2.name));
						}
					});
				}
				columnsPanel.removeAll();
				java.awt.Color bgDarker = new java.awt.Color(255, 255, 206);
				boolean hasConstraints = false;
				for (Column column: columns) {
					hasConstraints = hasConstraints || !column.isNullable  || column.isVirtual ||  column.isIdentityColumn;
				}
				rows.clear();
				for (Column column: columns) {
					boolean isPk = false;
					if (table.primaryKey.getColumns() != null) {
						for (Column pk: table.primaryKey.getColumns()) {
							if (pk.name.equals(column.name)) {
								isPk = true;
							}
						}
					}
					
					JPanel panel = new JPanel();
					rows.put(column.name, panel);
					if (y % 2 == 0) {
						panel.setOpaque(false);
					} else {
						panel.setBackground(bgDarker);
					}
			        panel.setLayout(new java.awt.GridBagLayout());
			        if (column.name.equals(foundColumn)) {
			        	panel.setOpaque(true);
			        	panel.setBackground(selectedBG);
			        }
		
			        JLabel label;
			        label = new JLabel();
			        
			        if (isPk) {
			        	label.setForeground(Color.red);
			        }
			        
			        label.setText(Quoting.staticUnquote(column.name));
			        GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
			        gridBagConstraints.gridx = 1;
			        gridBagConstraints.gridy = y;
			        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
			        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
			        gridBagConstraints.weightx = 1.0;
			        panel.add(label, gridBagConstraints);

			        if (row != null) {
			        	final JLabel finalLabel = label;
			        	label = new JLabel() {
							@Override
							public Dimension getPreferredSize() {
								Dimension preferredSize = super.getPreferredSize();
						        int maxWidth = TableDetailsView.this.getWidth() - finalLabel.getPreferredSize().width - 34;
						        if (maxWidth < 1) {
						        	maxWidth = 1;
						        }
								preferredSize.setSize(Math.min(preferredSize.width, maxWidth), preferredSize.height);
								return preferredSize;
							}
			        	};
				        Object obj = columnValue.get(column);
				        label.setForeground(obj == null? Color.gray : Color.black);
				        if (obj == null) {
				        	label.setFont(italic);
				        }
						label.setText(String.valueOf(obj));
				        gridBagConstraints = new java.awt.GridBagConstraints();
				        gridBagConstraints.gridx = 2;
				        gridBagConstraints.gridy = y;
				        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
				        gridBagConstraints.weightx = 1.0;
				        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
				        panel.add(label, gridBagConstraints);
			        } else {
				        label = new JLabel();
				        label.setForeground(Color.gray);
				        label.setText(column.toSQL("").substring(column.name.length()).trim());
				        gridBagConstraints = new java.awt.GridBagConstraints();
				        gridBagConstraints.gridx = 2;
				        gridBagConstraints.gridy = y;
				        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
				        gridBagConstraints.weightx = 1.0;
				        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
				        panel.add(label, gridBagConstraints);
			
				        StringBuilder constraints = new StringBuilder();
				        
				        if (!column.isNullable) {
				        	constraints.append("not null ");
				        }
				        if (column.isVirtual) {
				        	constraints.append("virtual ");
				        }
				        if (column.isIdentityColumn) {
				        	constraints.append("identity ");
				        }
				        
				        if (hasConstraints) {
					        label = new JLabel();
					        label.setForeground(Color.gray);
					        JPanel panelC = new JPanel();
							if (y % 2 == 0) {
								panelC.setOpaque(false);
							} else {
								panelC.setBackground(bgDarker);
							}
							if (column.name.equals(foundColumn)) {
								panelC.setOpaque(true);
								panelC.setBackground(selectedBG);
					        }
							panelC.setLayout(new java.awt.GridBagLayout());
					        if (column.type.toUpperCase(Locale.ENGLISH).equals(column.type)) {
								label.setText(" " + constraints.toString().toUpperCase(Locale.ENGLISH));
				        	} else {
								label.setText(" " + constraints.toString());
				        	}
					        gridBagConstraints = new java.awt.GridBagConstraints();
					        gridBagConstraints.gridx = 1;
					        gridBagConstraints.gridy = 1;
					        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
					        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
					        gridBagConstraints.weightx = 1;
					        panelC.add(label, gridBagConstraints);
					        gridBagConstraints = new java.awt.GridBagConstraints();
					        gridBagConstraints.gridx = 3;
					        gridBagConstraints.gridy = y;
					        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
					        gridBagConstraints.weightx = 0;
					        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
					        columnsPanel.add(panelC, gridBagConstraints);
				        }
			        }
		
			        gridBagConstraints = new java.awt.GridBagConstraints();
			        gridBagConstraints.gridx = 1;
			        gridBagConstraints.gridy = y;
			        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
			        gridBagConstraints.weightx = 1;
			        columnsPanel.add(panel, gridBagConstraints);
		
			        ++y;
				}
			}
		};
		updateColumnsTable.run();
        if (currentView != null) {
        	jPanel1.scrollRectToVisible(currentView.jPanel1.getVisibleRect());
        }
    }

    private String foundColumn;

    private void findColumns(final int x, final int y) {
		List<String> columNames = new ArrayList<String>();
		Map<String, Integer> columNamesCount = new HashMap<String, Integer>();
		for (String nameObj: rows.keySet()) {
			if (nameObj != null) {
				String name = nameObj.toString();
				if (columNamesCount.containsKey(name)) {
					columNamesCount.put(name, columNamesCount.get(name) + 1);
				} else {
					columNames.add(name);
					columNamesCount.put(name, 1);
				}
			}
		}
		Collections.sort(columNames, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1.compareToIgnoreCase(o2);
			}
		});

		final Window owner = SwingUtilities.getWindowAncestor(this);

		final JComboBox2 combobox = new JComboBox2();
		combobox.setModel(new DefaultComboBoxModel(columNames.toArray()));
		StringSearchPanel searchPanel = new StringSearchPanel(null, combobox, null, null, null, new Runnable() {
			@Override
			public void run() {
				Object selected = combobox.getSelectedItem();
				if (selected != null) {
					foundColumn = null;
					for (String name: rows.keySet()) {
						if (name != null && name.equals(selected)) {
							Rectangle visibleRect = jPanel1.getVisibleRect();
							Rectangle cellRect = rows.get(name).getBounds();
							foundColumn = name;
							updateColumnsTable.run();
							jPanel1.scrollRectToVisible(
									new Rectangle(
											visibleRect.x + visibleRect.width / 2, 
											cellRect.y + columnsPanel.getBounds().y - 32, 
											1, cellRect.height + 64));
							break;
						}
					}
				}
			}
		}) {
			@Override
			protected Integer preferredWidth() {
				return 260;
			}
			@Override
			protected Integer maxX() {
				if (owner != null) {
					return owner.getX() + owner.getWidth() - preferredWidth();
				} else {
					return null;
				}
			}
			@Override
			protected Integer maxY(int height) {
				if (owner != null) {
					return owner.getY() + owner.getHeight() - height - 8;
				} else {
					return null;
				}
			}
		};

		searchPanel.setStringCount(columNamesCount);
		searchPanel.find(owner, "Find Column", x, y, true);
	}

    public boolean isCacheable() {
		return cacheable;
	}

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        tableNameLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        columnsPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        warnPanel = new javax.swing.JPanel();
        warnLabel = new javax.swing.JLabel();
        analyseButton = new javax.swing.JButton();
        warnLabel1 = new javax.swing.JLabel();
        warnLabel2 = new javax.swing.JLabel();
        sortColumnsCheckBox = new javax.swing.JCheckBox();
        findColumnsPanel = new javax.swing.JPanel();
        findColumnsLabel = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 230));
        setLayout(new java.awt.GridBagLayout());

        jScrollPane1.setBorder(null);

        jPanel1.setBackground(new java.awt.Color(255, 255, 230));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        tableNameLabel.setText("Test");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
        jPanel1.add(tableNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel1.add(jSeparator1, gridBagConstraints);

        columnsPanel.setOpaque(false);
        columnsPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        jPanel1.add(columnsPanel, gridBagConstraints);

        jLabel1.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.weighty = 1.0;
        jPanel1.add(jLabel1, gridBagConstraints);

        warnPanel.setOpaque(false);
        warnPanel.setLayout(new java.awt.GridBagLayout());

        warnLabel.setText("  ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        warnPanel.add(warnLabel, gridBagConstraints);

        analyseButton.setText("jButton1");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        warnPanel.add(analyseButton, gridBagConstraints);

        warnLabel1.setText("Data model differs from");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        warnPanel.add(warnLabel1, gridBagConstraints);

        warnLabel2.setText("database table definition");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        warnPanel.add(warnLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 12, 2, 0);
        jPanel1.add(warnPanel, gridBagConstraints);

        jScrollPane1.setViewportView(jPanel1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(jScrollPane1, gridBagConstraints);

        sortColumnsCheckBox.setText("sort columns      ");
        sortColumnsCheckBox.setOpaque(false);
        sortColumnsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortColumnsCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(sortColumnsCheckBox, gridBagConstraints);

        findColumnsPanel.setOpaque(false);
        findColumnsPanel.setLayout(new java.awt.GridBagLayout());

        findColumnsLabel.setText("Find Columns");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 6);
        findColumnsPanel.add(findColumnsLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 2);
        add(findColumnsPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void sortColumnsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortColumnsCheckBoxActionPerformed
		updateColumnsTable.run();
		repaint();
    }//GEN-LAST:event_sortColumnsCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton analyseButton;
    private javax.swing.JPanel columnsPanel;
    private javax.swing.JLabel findColumnsLabel;
    public javax.swing.JPanel findColumnsPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JCheckBox sortColumnsCheckBox;
    private javax.swing.JLabel tableNameLabel;
    private javax.swing.JLabel warnLabel;
    private javax.swing.JLabel warnLabel1;
    private javax.swing.JLabel warnLabel2;
    private javax.swing.JPanel warnPanel;
    // End of variables declaration//GEN-END:variables
}
