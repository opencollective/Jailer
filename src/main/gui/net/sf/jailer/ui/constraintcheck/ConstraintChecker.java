/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jailer.ui.constraintcheck;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.fife.rsta.ui.EscapableDialog;
import org.fife.ui.rtextarea.RTextScrollPane;

import net.sf.jailer.database.Session;
import net.sf.jailer.database.Session.ResultSetReader;
import net.sf.jailer.datamodel.Association;
import net.sf.jailer.datamodel.Column;
import net.sf.jailer.datamodel.DataModel;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.ui.UIUtil;
import net.sf.jailer.ui.syntaxtextarea.RSyntaxTextAreaWithSQLSyntaxStyle;
import net.sf.jailer.util.CancellationHandler;
import net.sf.jailer.util.SqlUtil;

/**
 *
 * @author RalfW
 */
public abstract class ConstraintChecker extends javax.swing.JPanel {

	private static final Object FIX_SET_NULL = "Set Reference = null";
	private static final Object FIX_DELETE = "Delete Child";
	private int numChecks;
	private int numChecksDone;
	private int numErrors;
	private Object context = new Object();
	private RSyntaxTextAreaWithSQLSyntaxStyle editorPane;
	private DefaultTableModel problemsModel;
	private JDialog dialog;
	
    /**
     * Creates new form ConstraintChecker
     * @param session 
     */
    public ConstraintChecker(JFrame owner, DataModel dataModel, final Session session) {
        initComponents();
        
        editorPane = new RSyntaxTextAreaWithSQLSyntaxStyle(false, false);
        editorPane.setEditable(false);
        RTextScrollPane jScrollPane = new RTextScrollPane();
        jScrollPane.setViewportView(editorPane);
        fixesPanel.add(jScrollPane);
        jScrollPane.setLineNumbersEnabled(true);

        statusLabel.setVisible(false);
        childLabel.setVisible(false);
        parentLabel.setVisible(false);
        conditionLabel.setVisible(false);
        problemLabel.setVisible(false);
        
        fixesBorderPanel.setVisible(false);
        
		problemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		problemsModel = new DefaultTableModel(new String[] { "Child", "Parent", "Problem", "Fix" }, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 3;
			}

			@Override
			public void setValueAt(Object aValue, int row, int column) {
				super.setValueAt(aValue, row, column);
				updateFixes();
			}
		};
		JComboBox fixField = new net.sf.jailer.ui.JComboBox();
	    // fixField.setBorder(new LineBorder(Color.black));
		fixField.setModel(new DefaultComboBoxModel(new Object[] { FIX_SET_NULL, FIX_DELETE, "" }));
		DefaultCellEditor anEditor = new DefaultCellEditor(fixField);
		anEditor.setClickCountToStart(1);
		problemsTable.setDefaultEditor(problemsTable.getColumnClass(3), anEditor);
		problemsTable.setModel(problemsModel);
		final TableCellRenderer defaultTableCellRenderer = problemsTable.getDefaultRenderer(String.class);
		problemsTable.setDefaultRenderer(Object.class, new TableCellRenderer() {
			final Color BG1 = new Color(255, 255, 255);
			final Color BG2 = new Color(230, 255, 255);

			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component render = defaultTableCellRenderer.getTableCellRendererComponent(table, value, isSelected, false, row, column);
				if (render instanceof JLabel) {
					if (!isSelected) {
						((JLabel) render).setBackground((row % 2 == 0) ? BG1 : BG2);
					}
					((JLabel) render).setToolTipText(String.valueOf(value));
				}
				return render;
			}
		});
		problemsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int i = problemsTable.getSelectedRow();
				if (i >= 0) {
					final int MAX_LENGTH = 150;
					
					Problem problem = problems.get(i);
			        childLabel.setVisible(true);
			        parentLabel.setVisible(true);
			        conditionLabel.setVisible(true);
			        problemLabel.setVisible(true);
			        childLabel.setText(problem.association.source.getName());
			        parentLabel.setText(problem.association.destination.getName());
			        String condition = SqlUtil.replaceAliases(problem.association.getJoinCondition(), problem.association.source.getName(), problem.association.destination.getName());
			        conditionLabel.setToolTipText(condition);
			        if (condition.length() > MAX_LENGTH) {
			        	condition = condition.substring(0, MAX_LENGTH) + "...";
			        }
			        conditionLabel.setText(condition);
			        String err = problem.description;
			        problemLabel.setToolTipText(err);
			        if (err.length() > MAX_LENGTH) {
			        	err = err.substring(0, MAX_LENGTH) + "...";
			        }
			        problemLabel.setText(err);
			        viewButton.setEnabled(problem.where != null);
				} else {
			        viewButton.setEnabled(false);
				}
			}
		});
		problemsTable.addMouseListener(new MouseListener() {
			private JPopupMenu lastMenu;

			@Override
			public void mouseReleased(MouseEvent e) {
			}

			@Override
			public void mousePressed(MouseEvent e) {
				int ri = problemsTable.rowAtPoint(e.getPoint());
				if (ri >= 0 && !problems.isEmpty()) {
					Problem problem = problems.get(ri);
					if (e.getClickCount() > 1) {
						if (problem.where != null) {
							openTableBrowser(problem.source, problem.where);
						}
					}
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
			}

			@Override
			public void mouseEntered(MouseEvent e) {
			}

			@Override
			public void mouseClicked(MouseEvent e) {
			}
		});

        dialog = new EscapableDialog(owner, "Consistency Check ") {
        };
		dialog.getContentPane().add(this);
		dialog.pack();
		dialog.setSize(800, 600);
		dialog.setLocation(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2, Math.max(0, owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2));
		UIUtil.fit(dialog);
		
		List<Runnable> checks = new ArrayList<Runnable>();
		for (Table table: dataModel.getSortedTables()) {
			
			// TODO
			Map<String, Integer> rlCount = new HashMap<String, Integer>();
			
			boolean skip = false;
			for (final Association a: table.associations) {
				if (a.isInsertDestinationBeforeSource()) {
					Map<Column, Column> mapping = a.createSourceToDestinationKeyMapping();
					for (Column e: mapping.keySet()) {
						if (!rlCount.containsKey(e.name)) {
							rlCount.put(e.name, 1);
						} else {
							if (!e.name.equalsIgnoreCase("rzmandant")) {
								skip  = true;
							}
						}
					}
				}
			}
			if (skip) {
				continue;
			}
			
			for (final Association a: table.associations) {
				if (a.isInsertDestinationBeforeSource()) {
					Map<Column, Column> mapping = a.createSourceToDestinationKeyMapping();
					if (!mapping.isEmpty()) {
						System.err.println(table.getName() + " " + mapping);
						StringBuilder sql = new StringBuilder("Select count(*) from ");
						StringBuilder where = new StringBuilder("not exists(Select * from ");
						sql.append(a.source.getName() + " A left join " + a.destination.getName() + " B on ");
						where.append(a.destination.getName() + " B where ");
						boolean f = true;
						for (Entry<Column, Column> e: mapping.entrySet()) {
							if (!f) {
								sql.append(" and ");
								where.append(" and ");
							}
							f = false;
							sql.append("A." + e.getKey().name + " = B." + e.getValue().name);
							where.append("A." + e.getKey().name + " = B." + e.getValue().name);
						}
						sql.append(" Where ");
						where.append(") and (");
						f = true;
						for (Entry<Column, Column> e: mapping.entrySet()) {
							if (!f) {
								sql.append(" and ");  // TODO " or "
								where.append(" and ");  // TODO " or "
							}
							f = false;
							sql.append("A." + e.getKey().name + " is not null and B." + e.getValue().name + " is null");
							where.append("A." + e.getKey().name + " is not null");
						}
						where.append(")");
						
						final String checkQuery = sql.toString();
						final String finalWhere = where.toString();
						String ref = null;
						for (Column c: mapping.keySet()) {
							if (!c.name.equalsIgnoreCase("rzmandant")) {
								ref = c.name;
							}
						}
						final String referenceColumn = ref;
						checks.add(new Runnable() {
							@Override
							public void run() {
								try {
									final long[] count = new long[1];
									session.executeQuery(checkQuery, new ResultSetReader() {
										@Override
										public void readCurrentRow(ResultSet resultSet) throws SQLException {
											count[0] = resultSet.getLong(1);
										}
										@Override
										public void close() throws SQLException {
										}
									}, null, context, 0);
									addResult(a, count[0], checkQuery, null, finalWhere, referenceColumn);
								} catch (SQLException e) {
									addResult(a, 0, checkQuery, e, null, null);
								}
							}
						});
					}
				}
			}
		}

		numChecks = checks.size();
		queue.addAll(checks);
		queue.add(new Runnable() {
			@Override
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						statusLabel.setVisible(true);
						if (numErrors > 0) {
							statusLabel.setText(numErrors + " problems");
							statusLabel.setForeground(Color.RED);
						}
					}
				});
			}
		});
		jProgressBar1.setMaximum(numChecks == 0? 1 : numChecks);
		dialog.addWindowListener(new WindowListener() {
			@Override
			public void windowOpened(WindowEvent e) {
			}
			@Override
			public void windowIconified(WindowEvent e) {
			}
			@Override
			public void windowDeiconified(WindowEvent e) {
			}
			@Override
			public void windowDeactivated(WindowEvent e) {
			}
			@Override
			public void windowClosing(WindowEvent e) {
			}
			@Override
			public void windowClosed(WindowEvent e) {
				CancellationHandler.cancelSilently(context);
			}
			@Override
			public void windowActivated(WindowEvent e) {
			}
		});
		dialog.setVisible(true);
    }

	protected void updateFixes() {
		editorPane.setText("");
		for (int i = 0; i < problemsModel.getRowCount(); ++i) {
			Object fix = problemsModel.getValueAt(i, 3);
			Problem problem = problems.get(i);
			if (problem.source != null && problem.where != null) {
				String suffix = " (!intentionally invalid);\n";
				if (FIX_DELETE.equals(fix)) {
					editorPane.append("Delete from " + problem.source.getName() + " A where " + problem.where + suffix);
				} else if (FIX_SET_NULL.equals(fix)) {
					editorPane.append("Update " + problem.source.getName() + " A set " + problem.referenceColumn + " = null where " + problem.where + suffix);
				}
			}
		}
		sqlConsoleButton.setEnabled(editorPane.getText().length() > 0);
	}

	private static class Problem {
		Table source;
    	Table destination;
    	Association association;
    	String where;
    	String referenceColumn;
    	String description;
    }
    
    private List<Problem> problems = new ArrayList<Problem>();
    
    private void addResult(final Association a, final long count, final String checkQuery, final SQLException exception, final String where, final String referenceColumn) {
    	SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
		    	if (count > 0 || exception != null) {
					++numErrors;
					Problem problem = new Problem();
					problem.source = a.source;
					problem.destination = a.destination;
					problem.association = a;
					problem.where = where;
					problem.referenceColumn = referenceColumn;
					problem.description = exception != null? exception.getMessage() : (count + " invalid references");
					problems.add(problem);
					problemsModel.addRow(new Object[] { problem.source.getName(), problem.destination.getName(), problem.description });
				}
		    	++numChecksDone;
		    	progressLabel.setText(numChecksDone + " of " + numChecks);
		    	jProgressBar1.setValue(numChecksDone);
		    	if (numErrors > 0) {
		    		jProgressBar1.setForeground(Color.red);
		    		progressLabel.setForeground(Color.red);
		    	}
			}
		});
	}

	/**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        jProgressBar1 = new javax.swing.JProgressBar();
        progressLabel = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        problemsTable = new javax.swing.JTable();
        statusLabel = new javax.swing.JLabel();
        fixesBorderPanel = new javax.swing.JPanel();
        fixesPanel = new javax.swing.JPanel();
        sqlConsoleButton = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        viewButton = new javax.swing.JButton();
        childLabel = new javax.swing.JLabel();
        parentLabel = new javax.swing.JLabel();
        conditionLabel = new javax.swing.JLabel();
        problemLabel = new javax.swing.JLabel();
        dummyLabel = new javax.swing.JLabel();
        closeButton = new javax.swing.JButton();

        setLayout(new java.awt.GridBagLayout());

        jPanel1.setLayout(new java.awt.GridBagLayout());

        jProgressBar1.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        jPanel1.add(jProgressBar1, gridBagConstraints);

        progressLabel.setText("0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel1.add(progressLabel, gridBagConstraints);

        jPanel3.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Problems"));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        problemsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(problemsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel2.add(jScrollPane1, gridBagConstraints);

        statusLabel.setForeground(new java.awt.Color(0, 105, 0));
        statusLabel.setText("Database is consistent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel2.add(statusLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 2.0;
        gridBagConstraints.weighty = 1.0;
        jPanel3.add(jPanel2, gridBagConstraints);

        fixesBorderPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Fixes"));
        fixesBorderPanel.setLayout(new java.awt.GridBagLayout());

        fixesPanel.setLayout(new java.awt.BorderLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        fixesBorderPanel.add(fixesPanel, gridBagConstraints);

        sqlConsoleButton.setText("SQL Console");
        sqlConsoleButton.setEnabled(false);
        sqlConsoleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sqlConsoleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        fixesBorderPanel.add(sqlConsoleButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        jPanel3.add(fixesBorderPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel1.add(jPanel3, gridBagConstraints);

        jPanel7.setLayout(new java.awt.GridBagLayout());

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Details"));
        jPanel5.setLayout(new java.awt.GridBagLayout());

        jPanel6.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Child");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel6.add(jLabel1, gridBagConstraints);

        jLabel2.setText("Parent");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel6.add(jLabel2, gridBagConstraints);

        jLabel3.setText("Condition");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel6.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Problem");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel6.add(jLabel4, gridBagConstraints);

        viewButton.setText("View");
        viewButton.setEnabled(false);
        viewButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel6.add(viewButton, gridBagConstraints);

        childLabel.setForeground(new java.awt.Color(0, 100, 0));
        childLabel.setText("jLabel5");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        jPanel6.add(childLabel, gridBagConstraints);

        parentLabel.setForeground(new java.awt.Color(95, 0, 0));
        parentLabel.setText("jLabel5");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        jPanel6.add(parentLabel, gridBagConstraints);

        conditionLabel.setForeground(java.awt.Color.blue);
        conditionLabel.setText("jLabel5");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        jPanel6.add(conditionLabel, gridBagConstraints);

        problemLabel.setForeground(java.awt.Color.red);
        problemLabel.setText("jLabel5");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        jPanel6.add(problemLabel, gridBagConstraints);

        dummyLabel.setForeground(java.awt.Color.red);
        dummyLabel.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
        jPanel6.add(dummyLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel5.add(jPanel6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        jPanel7.add(jPanel5, gridBagConstraints);

        closeButton.setText("Close");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        jPanel7.add(closeButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 50;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        jPanel1.add(jPanel7, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(jPanel1, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        dialog.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void sqlConsoleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sqlConsoleButtonActionPerformed
    	appendSQLConsole(editorPane.getText());
        dialog.dispose();
    }//GEN-LAST:event_sqlConsoleButtonActionPerformed

    private void viewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewButtonActionPerformed
    	int i = problemsTable.getSelectedRow();
		if (i >= 0) {
			Problem problem = problems.get(i);
			openTableBrowser(problem.source, problem.where);
		}
    }//GEN-LAST:event_viewButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel childLabel;
    private javax.swing.JButton closeButton;
    private javax.swing.JLabel conditionLabel;
    private javax.swing.JLabel dummyLabel;
    private javax.swing.JPanel fixesBorderPanel;
    private javax.swing.JPanel fixesPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel parentLabel;
    private javax.swing.JLabel problemLabel;
    private javax.swing.JTable problemsTable;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JButton sqlConsoleButton;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JButton viewButton;
    // End of variables declaration//GEN-END:variables

	static final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
    static {
    	Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				for (;;) {
					try {
						queue.take().run();
					} catch (Throwable t) {
					}
				}
			}
		});
        thread.setDaemon(true);
        thread.start();
    }

    protected abstract void openTableBrowser(Table source, String where);
    protected abstract void appendSQLConsole(String text);
    
}

