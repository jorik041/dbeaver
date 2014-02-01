/*
 * Copyright (C) 2010-2014 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ext.mssql.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.mssql.MSSQLConstants;
import org.jkiss.dbeaver.ext.mssql.MSSQLUtils;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCColumnKeyType;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableColumn;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableColumn;
import org.jkiss.dbeaver.ui.properties.IPropertyValueListProvider;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MSSQLTableColumn
 */
public class MSSQLTableColumn extends JDBCTableColumn<MSSQLTableBase> implements DBSTableColumn
{
    static final Log log = LogFactory.getLog(MSSQLTableColumn.class);

    private static Pattern enumPattern = Pattern.compile("'([^']*)'");

    public static enum KeyType implements JDBCColumnKeyType {
        PRI,
        UNI,
        MUL;

        @Override
        public boolean isInUniqueKey()
        {
            return this == PRI || this == UNI;
        }

        @Override
        public boolean isInReferenceKey()
        {
            return this == MUL;
        }
    }

    private String comment;
    private long charLength;
    private MSSQLCollation collation;
    private KeyType keyType;

    private List<String> enumValues;

    public MSSQLTableColumn(MSSQLTableBase table)
    {
        super(table, false);
    }

    public MSSQLTableColumn(
        MSSQLTableBase table,
        ResultSet dbResult)
        throws DBException
    {
        super(table, true);
        loadInfo(dbResult);
    }

    private void loadInfo(ResultSet dbResult)
        throws DBException
    {
        setName(JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_NAME));
        setOrdinalPosition(JDBCUtils.safeGetInt(dbResult, MSSQLConstants.COL_ORDINAL_POSITION));
        String typeName = JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_DATA_TYPE);
        String keyTypeName = JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_KEY);
        if (!CommonUtils.isEmpty(keyTypeName)) {
            try {
                keyType = KeyType.valueOf(keyTypeName);
            } catch (IllegalArgumentException e) {
                log.debug(e);
            }
        }
        setTypeName(typeName);
        setValueType(MSSQLUtils.typeNameToValueType(typeName));
        DBSDataType dataType = getDataSource().getDataType(typeName);
        this.charLength = JDBCUtils.safeGetLong(dbResult, MSSQLConstants.COL_CHARACTER_MAXIMUM_LENGTH);
        if (this.charLength <= 0) {
            if (dataType != null) {
                setMaxLength(dataType.getPrecision());
            }
        } else {
            setMaxLength(this.charLength);
        }
        this.comment = JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_COMMENT);
        setRequired(!"YES".equals(JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_IS_NULLABLE)));
        setScale(JDBCUtils.safeGetInt(dbResult, MSSQLConstants.COL_NUMERIC_SCALE));
        setPrecision(JDBCUtils.safeGetInt(dbResult, MSSQLConstants.COL_NUMERIC_PRECISION));
        setDefaultValue(JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_DEFAULT));
        this.collation = getDataSource().getCollation(JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLLATION_NAME));

        String extra = JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_EXTRA);
        this.autoGenerated = extra != null && extra.contains(MSSQLConstants.EXTRA_AUTO_INCREMENT);

        String typeDesc = JDBCUtils.safeGetString(dbResult, MSSQLConstants.COL_COLUMN_TYPE);
        if (!CommonUtils.isEmpty(typeDesc) &&
            (typeName.equalsIgnoreCase(MSSQLConstants.TYPE_NAME_ENUM) || typeName.equalsIgnoreCase(MSSQLConstants.TYPE_NAME_SET)))
        {
            enumValues = new ArrayList<String>();
            Matcher enumMatcher = enumPattern.matcher(typeDesc);
            while (enumMatcher.find()) {
                String enumStr = enumMatcher.group(1);
                enumValues.add(enumStr);
            }
        }
    }

    @NotNull
    @Override
    public MSSQLDataSource getDataSource()
    {
        return getTable().getDataSource();
    }

    public long getCharLength()
    {
        return charLength;
    }

    @Property(viewable = true, editable = true, updatable = true, order = 20, listProvider = ColumnTypeNameListProvider.class)
    @Override
    public String getTypeName()
    {
        return super.getTypeName();
    }

    @Property(viewable = true, editable = true, updatable = true, order = 40)
    @Override
    public long getMaxLength()
    {
        return super.getMaxLength();
    }

    @Property(viewable = true, editable = true, updatable = true, order = 50)
    @Override
    public boolean isRequired()
    {
        return super.isRequired();
    }

    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 51)
    public boolean isAutoGenerated()
    {
        return autoGenerated;
    }

    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 70)
    public String getDefaultValue()
    {
        return super.getDefaultValue();
    }

    @Override
    @Property(viewable = true, order = 80)
    public KeyType getKeyType()
    {
        return keyType;
    }

    public List<String> getEnumValues()
    {
        return enumValues;
    }

    @Property(viewable = false, editable = true, listProvider = CharsetListProvider.class, order = 81)
    public MSSQLCharset getCharset()
    {
        return collation == null ? null : collation.getCharset();
    }

    public void setCharset(MSSQLCharset charset)
    {
        this.collation = charset == null ? null : charset.getDefaultCollation();
    }

    @Property(viewable = false, editable = true, listProvider = CollationListProvider.class, order = 82)
    public MSSQLCollation getCollation()
    {
        return collation;
    }

    public void setCollation(MSSQLCollation collation)
    {
        this.collation = collation;
    }

    @Property(viewable = true, editable = true, updatable = true, order = 100)
    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public static class CharsetListProvider implements IPropertyValueListProvider<MSSQLTableColumn> {
        @Override
        public boolean allowCustomValue()
        {
            return false;
        }
        @Override
        public Object[] getPossibleValues(MSSQLTableColumn object)
        {
            return object.getDataSource().getCharsets().toArray();
        }
    }

    public static class CollationListProvider implements IPropertyValueListProvider<MSSQLTableColumn> {
        @Override
        public boolean allowCustomValue()
        {
            return false;
        }
        @Override
        public Object[] getPossibleValues(MSSQLTableColumn object)
        {
            if (object.getCharset() == null) {
                return null;
            } else {
                return object.getCharset().getCollations().toArray();
            }
        }
    }
}
