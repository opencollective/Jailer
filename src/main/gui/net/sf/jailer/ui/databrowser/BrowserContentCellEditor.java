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
package net.sf.jailer.ui.databrowser;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.sf.jailer.database.Session;
import net.sf.jailer.datamodel.Column;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.util.CellContentConverter;
import net.sf.jailer.util.CellContentConverter.TimestampWithNano;
import net.sf.jailer.util.SqlUtil;

/**
 * For in-place editing of query results.
 * 
 * @author Ralf Wisser
 */
public class BrowserContentCellEditor {
	
	/**
	 * Cell content to text converter.
	 */
	private enum Converter {
		DECMAL {
			@Override
			String cellContentToText(int columnType, Object content) {
				if (content instanceof BigDecimal) {
					return SqlUtil.toString((BigDecimal) content);
				}
				if (content instanceof Double) {
					return SqlUtil.toString((Double) content);
				}
				if (content instanceof Float) {
					return SqlUtil.toString(((Float) content).doubleValue());
				}
				if (content instanceof Number) {
					return ((Number) content).toString();
				}
				return "";
			}

			@Override
			Object textToContent(int columnType, String text, Object oldContent) {
				return new BigDecimal(text);
			}

			@Override
			boolean isEditable(int columnType, Object content) {
				return true;
			}
		},
		CHAR {
			@Override
			String cellContentToText(int columnType, Object content) {
				return String.valueOf(content);
			}

			@Override
			Object textToContent(int columnType, String text, Object oldContent) {
				return text;
			}

			@Override
			boolean isEditable(int columnType, Object content) {
				return content == null || (content instanceof String && !(content.toString().indexOf('\n') >= 0 || content.toString().indexOf('\t') >= 0));
			}
		},
		NCHAR {
			@Override
			String cellContentToText(int columnType, Object content) {
				return String.valueOf(content);
			}

			@Override
			Object textToContent(int columnType, String text, Object oldContent) {
				return new CellContentConverter.NCharWrapper(text);
			}

			@Override
			boolean isEditable(int columnType, Object content) {
				return content == null || (content instanceof CellContentConverter.NCharWrapper && !(content.toString().indexOf('\n') >= 0 || content.toString().indexOf('\t') >= 0));
			}
		},
		DATE {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
			SimpleDateFormat timeStampWONFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

			@Override
			String cellContentToText(int columnType, Object content) {
				if (columnType == Types.DATE && (content == null || content instanceof Date)) {
					return dateFormat.format((Date) content);
				}
				return String.valueOf((Timestamp) content);
			}

			@Override
			Object textToContent(int columnType, String text, Object oldContent) {
				try {
					if (columnType == Types.DATE && (oldContent == null || oldContent instanceof Date)) {
						return new java.sql.Date(dateFormat.parse(text).getTime());
					}
					try {
						int dot = text.lastIndexOf('.');
						int nano = 0;
						if (dot > 0) {
							String n = text.substring(dot + 1);
							while (n.length() < 9) {
								n += "0";
							}
							if (n.length() > 9) {
								n = n.substring(0, 9);
							}
							nano = Integer.parseInt(n);
							text = text.substring(0, dot);
						}
						Timestamp result = new Timestamp(timeStampWONFormat.parse(text).getTime());
						result.setNanos(nano);
						return result;
					} catch (ParseException e) {
						if (text.length() <= 11) {
							return new java.sql.Timestamp(dateFormat.parse(text).getTime());
						}
					}
				} catch (ParseException e) {
					// ignore
				}
				return INVALID;
			}

			@Override
			boolean isEditable(int columnType, Object content) {
				return true;
			}
		},
		
		TIMESTAMP_WITH_NANO {
			SimpleDateFormat timeStampWONFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
			
			@Override
			String cellContentToText(int columnType, Object content) {
				String text = timeStampWONFormat.format((Timestamp) content);
				String nano = String.valueOf(1000000000L + ((TimestampWithNano) content).getNanos()).substring(1);
				while (nano.length() > 1 && nano.endsWith("0")) {
					nano = nano.substring(0, nano.length() - 1);
				}
				return text + "." + nano;
			}

			@Override
			Object textToContent(int columnType, String text, Object oldContent) {
				try {
					try {
						int dot = text.lastIndexOf('.');
						int nano = 0;
						if (dot > 0) {
							String n = text.substring(dot + 1);
							while (n.length() < 9) {
								n += "0";
							}
							if (n.length() > 9) {
								n = n.substring(0, 9);
							}
							nano = Integer.parseInt(n);
							text = text.substring(0, dot);
						}
						TimestampWithNano result = new TimestampWithNano(timeStampWONFormat.parse(text).getTime());
						result.setNanos(nano);
						return result;
					} catch (ParseException e) {
						if (text.length() <= 11) {
							return new java.sql.Timestamp(dateFormat.parse(text).getTime());
						}
					}
				} catch (ParseException e) {
					// ignore
				}
				return INVALID;
			}

			@Override
			boolean isEditable(int columnType, Object content) {
				return true;
			}
		};

		abstract boolean isEditable(int columnType, Object content);
		abstract String cellContentToText(int columnType, Object content);
		abstract Object textToContent(int columnType, String text, Object oldContent);
	}

	private Map<Integer, Converter> converterPerType = new HashMap<Integer, Converter>();
	{
		converterPerType.put(Types.DECIMAL, Converter.DECMAL);
		converterPerType.put(Types.DOUBLE, Converter.DECMAL);
		converterPerType.put(Types.FLOAT, Converter.DECMAL);
		converterPerType.put(Types.INTEGER, Converter.DECMAL);
		converterPerType.put(Types.NUMERIC, Converter.DECMAL);
		converterPerType.put(Types.SMALLINT, Converter.DECMAL);
		converterPerType.put(Types.TINYINT, Converter.DECMAL);
		converterPerType.put(Types.BIGINT, Converter.DECMAL);
		
		converterPerType.put(Types.CHAR, Converter.CHAR);
		converterPerType.put(Types.NCHAR, Converter.NCHAR);
		converterPerType.put(Types.LONGNVARCHAR, Converter.NCHAR);
		converterPerType.put(Types.LONGVARCHAR, Converter.CHAR);
		converterPerType.put(Types.NVARCHAR, Converter.NCHAR);
		converterPerType.put(Types.VARCHAR, Converter.CHAR);

		converterPerType.put(Types.DATE, Converter.DATE);
		converterPerType.put(Types.TIMESTAMP, Converter.DATE);

		converterPerType.put(CellContentConverter.TIMESTAMP_WITH_NANO, Converter.TIMESTAMP_WITH_NANO);
	}
	
	/**
	 * {@link Types} per column.
	 */
	private final int[] columnTypes;

	/**
	 * The session.
	 */
	private final Session session;
	
	/**
	 * Constructor.
	 * 
	 * @param columnTypes {@link Types} per column
	 * @param columnTypeNames type names per column
	 * @param session the session
	 */
	public BrowserContentCellEditor(int[] columnTypes, String[] columnTypeNames, Session session) {
		this.columnTypes = columnTypes;
		this.session = session;
		if (session != null && session.dbms.getTimestampWithNanoTypeName() != null) {
			for (int i = 0; i < columnTypes.length && i < columnTypeNames.length; ++i) {
				if (session.dbms.getTimestampWithNanoTypeName().equalsIgnoreCase(columnTypeNames[i])) {
					columnTypes[i] = CellContentConverter.TIMESTAMP_WITH_NANO;
				}
			}
		}
	}

	/**
	 * Is given cell editable?
	 * 
	 * @param table the table 
	 * @param row row number
	 * @param column column number
	 * @param content cell content
	 */
	public boolean isEditable(Table table, int row, int column, Object content) {
		if (column < 0 || column >= columnTypes.length) {
			return false;
		}
		if (table == null || table.getColumns().size() <= column) {
			return false;
		}
		Column theColumn = table.getColumns().get(column);
		if (theColumn.name == null || theColumn.isVirtual()) {
			return false;
		}
		Converter converter = converterPerType.get(columnTypes[column]);
		if (converter != null) {
			return converter.isEditable(columnTypes[column], content);
		}
		return false;
	}

	/**
	 * Converts cell content to text.
	 * 
	 * @param row row number
	 * @param column column number
	 * @param content cell content
	 * 
	 * @return text
	 */
	public String cellContentToText(int row, int column, Object content) {
		if (content == null) {
			return "";
		}
		Converter converter = converterPerType.get(columnTypes[column]);
		if (converter != null) {
			try {
				return converter.cellContentToText(columnTypes[column], content);
			} catch (Exception e) {
				// ignore
			}
		}
		return "";
	}

	/**
	 * Converts text to cell content.
	 * 
	 * @param row row number
	 * @param column column number
	 * @param text the text
	 * 
	 * @return cell content or {@link #INVALID} if text cannot be converted
	 */
	public Object textToContent(int row, int column, String text, Object oldContent) {
		if (text.trim().equals("")) {
			return null;
		}
		Converter converter = converterPerType.get(columnTypes[column]);
		if (converter != null) {
			try {
				return converter.textToContent(columnTypes[column], text, oldContent);
			} catch (Exception e) {
				// ignore
			}
		}
		return INVALID;
	}

	/**
	 * Invalid content.
	 */
	public static final  Object INVALID = new Object();

}
