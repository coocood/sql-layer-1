/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.sql.optimizer.plan;

import com.foundationdb.server.collation.AkCollator;
import com.foundationdb.server.AkType;
import com.foundationdb.server.types.TPreptimeValue;
import com.foundationdb.sql.types.DataTypeDescriptor;
import com.foundationdb.sql.parser.ValueNode;

public interface ExpressionNode extends PlanElement
{
    public DataTypeDescriptor getSQLtype();
    @Deprecated
    public AkType getAkType();
    public ValueNode getSQLsource();
    public AkCollator getCollator();
    public TPreptimeValue getPreptimeValue();

    public void setPreptimeValue(TPreptimeValue value);
    public void setSQLtype(DataTypeDescriptor type);

    public boolean isColumn();
    public boolean isConstant();

    public boolean accept(ExpressionVisitor v);
    public ExpressionNode accept(ExpressionRewriteVisitor v);
}
