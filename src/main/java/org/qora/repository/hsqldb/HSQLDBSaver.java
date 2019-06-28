package org.qora.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Database helper for building, and executing, INSERT INTO ... ON DUPLICATE KEY UPDATE ... statements.
 * <p>
 * Columns, and corresponding values, are bound via close-coupled pairs in a chain thus:
 * <p>
 * {@code SaveHelper helper = new SaveHelper("TableName"); }<br>
 * {@code helper.bind("column_name", someColumnValue).bind("column2", columnValue2); }<br>
 * {@code helper.execute(repository); }<br>
 *
 */
public class HSQLDBSaver {

	private String table;

	private List<String> columns = new ArrayList<String>();
	private List<Object> objects = new ArrayList<Object>();

	/**
	 * Construct a SaveHelper, using SQL Connection and table name.
	 * 
	 * @param table
	 */
	public HSQLDBSaver(String table) {
		this.table = table;
	}

	/**
	 * Add a column, and bound value, to be saved when execute() is called.
	 * 
	 * @param column
	 * @param value
	 * @return the same SaveHelper object
	 */
	public HSQLDBSaver bind(String column, Object value) {
		columns.add(column);
		objects.add(value);
		return this;
	}

	/**
	 * Build PreparedStatement using bound column-value pairs then execute it.
	 * 
	 * @param repository
	 *
	 * @return the result from {@link PreparedStatement#execute()}
	 * @throws SQLException
	 */
	public boolean execute(HSQLDBRepository repository) throws SQLException {
		String sql = this.formatInsertWithPlaceholders();
		try (PreparedStatement preparedStatement = repository.connection.prepareStatement(sql)) {
			this.bindValues(preparedStatement);

			return preparedStatement.execute();
		}
	}

	/**
	 * Format table and column names into an INSERT INTO ... SQL statement.
	 * <p>
	 * Full form is:
	 * <p>
	 * INSERT INTO <I>table</I> (<I>column</I>, ...) VALUES (?, ...) ON DUPLICATE KEY UPDATE <I>column</I>=?, ...
	 * <p>
	 * Note that HSQLDB needs to put into mySQL compatibility mode first via "SET DATABASE SQL SYNTAX MYS TRUE" or "sql.syntax_mys=true" in connection URL.
	 * 
	 * @return String
	 */
	private String formatInsertWithPlaceholders() {
		String[] placeholders = new String[this.columns.size()];
		Arrays.setAll(placeholders, (int i) -> "?");

		StringBuilder output = new StringBuilder();
		output.append("INSERT INTO ");
		output.append(this.table);
		output.append(" (");
		output.append(String.join(", ", this.columns));
		output.append(") VALUES (");
		output.append(String.join(", ", placeholders));
		output.append(") ON DUPLICATE KEY UPDATE ");
		output.append(String.join("=?, ", this.columns));
		output.append("=?");
		return output.toString();
	}

	/**
	 * Binds objects to PreparedStatement based on INSERT INTO ... ON DUPLICATE KEY UPDATE ...
	 * <p>
	 * Note that each object is bound to <b>two</b> place-holders based on this SQL syntax:
	 * <p>
	 * INSERT INTO <I>table</I> (<I>column</I>, ...) VALUES (<b>?</b>, ...) ON DUPLICATE KEY UPDATE <I>column</I>=<b>?</b>, ...
	 * <p>
	 * Requires that mySQL SQL syntax support is enabled during connection.
	 * 
	 * @param preparedStatement
	 * @throws SQLException
	 */
	private void bindValues(PreparedStatement preparedStatement) throws SQLException {
		for (int i = 0; i < this.objects.size(); ++i) {
			Object object = this.objects.get(i);

			if (object instanceof BigDecimal) {
				// Special treatment for BigDecimals so that they retain their "scale",
				// which would otherwise be assumed as 0.
				preparedStatement.setBigDecimal(i + 1, (BigDecimal) object);
				preparedStatement.setBigDecimal(i + this.objects.size() + 1, (BigDecimal) object);
			} else if (object instanceof Timestamp) {
				// Special treatment for Timestamps so that they are stored as UTC
				preparedStatement.setTimestamp(i + 1, (Timestamp) object, Calendar.getInstance(HSQLDBRepository.UTC));
				preparedStatement.setTimestamp(i + this.objects.size() + 1, (Timestamp) object, Calendar.getInstance(HSQLDBRepository.UTC));
			} else {
				preparedStatement.setObject(i + 1, object);
				preparedStatement.setObject(i + this.objects.size() + 1, object);
			}
		}

	}

}
