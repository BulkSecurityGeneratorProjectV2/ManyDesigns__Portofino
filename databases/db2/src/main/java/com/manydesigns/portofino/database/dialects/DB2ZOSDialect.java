/*
 * Copyright (C) 2005-2020 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.manydesigns.portofino.database.dialects;

import org.hibernate.dialect.DB2390V8Dialect;

/**
 * @author Manuel Durán Aguete     - manuel@aguete.org
**/
@Deprecated
public class DB2ZOSDialect extends DB2390V8Dialect {
    public static final String copyright =
            "Copyright (C) 2005-2020 ManyDesigns srl";

    @Override
    public String getForUpdateString() {
        return " WITH RS USE AND KEEP EXCLUSIVE LOCKS";
    }

}
