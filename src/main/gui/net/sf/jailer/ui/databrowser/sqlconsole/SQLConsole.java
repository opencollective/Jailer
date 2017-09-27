/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jailer.ui.databrowser.sqlconsole;

import java.sql.SQLException;

import javax.swing.JScrollPane;

import org.fife.ui.autocomplete.AutoCompletion;

import net.sf.jailer.database.Session;
import net.sf.jailer.ui.databrowser.metadata.MetaDataSource;
import net.sf.jailer.ui.syntaxtextarea.RSyntaxTextAreaWithSQLCompletion;
import net.sf.jailer.util.Pair;

/**
 * SQL Console.
 *
 * @author Ralf Wisser
 */
@SuppressWarnings("serial")
public class SQLConsole extends javax.swing.JPanel {

	private Session session;
	MetaDataSource metaDataSource;
	private RSyntaxTextAreaWithSQLCompletion editorPane;
	private final MetaDataBasedSQLCompletionProvider provider;

	/**
	 * Creates new form SQLConsole
	 */
	public SQLConsole(Session session, MetaDataSource metaDataSource) throws SQLException {
		this.session = session;
		this.metaDataSource = metaDataSource;
		initComponents();

		this.editorPane = new RSyntaxTextAreaWithSQLCompletion() {
			@Override
			protected void actionPerformed() {
				Pair<Integer, Integer> loc = getCurrentStatementLocation(false);
				if (loc != null) {
					System.out.println(getText(loc.a, loc.b, true));
				}
			}
		};
		provider = new MetaDataBasedSQLCompletionProvider(session, metaDataSource);
		AutoCompletion ac = new AutoCompletion(provider);
		ac.install(editorPane);
		JScrollPane jScrollPane = new JScrollPane();
		jScrollPane.setViewportView(editorPane);
		consoleContainerPanel.add(jScrollPane);
		jScrollPane.setViewportView(editorPane);
	}

	/**
	 * Resets the console.
	 * 
	 * @param session
	 *            new session
	 * @param metaDataSource
	 *            new meta data source
	 */
	public void reset(Session session, MetaDataSource metaDataSource) throws SQLException {
		this.session = session;
		this.metaDataSource = metaDataSource;
		provider.reset(session, metaDataSource);
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed" desc="Generated
	// Code">//GEN-BEGIN:initComponents
	private void initComponents() {
		java.awt.GridBagConstraints gridBagConstraints;

		jPanel1 = new javax.swing.JPanel();
		consoleContainerPanel = new javax.swing.JPanel();

		setLayout(new java.awt.GridBagLayout());

		jPanel1.setLayout(new java.awt.GridBagLayout());

		consoleContainerPanel.setLayout(new java.awt.BorderLayout());
		gridBagConstraints = new java.awt.GridBagConstraints();
		gridBagConstraints.gridx = 1;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.weighty = 1.0;
		jPanel1.add(consoleContainerPanel, gridBagConstraints);

		gridBagConstraints = new java.awt.GridBagConstraints();
		gridBagConstraints.gridx = 1;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.weighty = 1.0;
		add(jPanel1, gridBagConstraints);
	}// </editor-fold>//GEN-END:initComponents
		// Variables declaration - do not modify//GEN-BEGIN:variables

	private javax.swing.JPanel consoleContainerPanel;
	private javax.swing.JPanel jPanel1;
	// End of variables declaration//GEN-END:variables

}
