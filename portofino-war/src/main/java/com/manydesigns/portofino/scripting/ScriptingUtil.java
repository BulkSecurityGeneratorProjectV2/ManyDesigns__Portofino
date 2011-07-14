/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.portofino.scripting;

import com.manydesigns.elements.ElementsThreadLocals;
import com.manydesigns.elements.ognl.OgnlUtils;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import ognl.OgnlContext;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class ScriptingUtil {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    public static Object runScript(String script,
                                   String scriptLanguage,
                                   Object root)
            throws Exception {
        OgnlContext ognlContext = ElementsThreadLocals.getOgnlContext();
        if ("ognl".equals(scriptLanguage)) {
            return OgnlUtils.getValueQuietly(script, ognlContext, root);
        } else if ("groovy".equals(scriptLanguage)) {
            ognlContext.put("root", root);
            Binding binding = new Binding(ognlContext);
            GroovyShell shell = new GroovyShell(binding);
            return shell.evaluate(script);
        } else {
            String msg = String.format(
                    "Unrecognised script language: %s", scriptLanguage);
            throw new IllegalArgumentException(msg);
        }
    }

}
