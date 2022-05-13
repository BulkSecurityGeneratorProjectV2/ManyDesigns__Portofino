package com.manydesigns.portofino.upstairs.actions.users;

import com.manydesigns.elements.messages.RequestMessages;
import com.manydesigns.portofino.database.model.Column;
import com.manydesigns.portofino.database.model.Table;
import com.manydesigns.portofino.persistence.Persistence;
import com.manydesigns.portofino.resourceactions.AbstractResourceAction;
import com.manydesigns.portofino.security.RequiresAdministrator;
import com.manydesigns.portofino.upstairs.actions.UpstairsAction;
import com.manydesigns.portofino.upstairs.actions.support.WizardInfo;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Alessio Stalla - alessiostalla@gmail.com
 */
@RequiresAuthentication
@RequiresAdministrator
public class UsersAction extends AbstractResourceAction {

    private static final Logger logger = LoggerFactory.getLogger(UsersAction.class);

    @Autowired
    protected Persistence persistence;

    @GET
    public Map getUsers() {
        return security.getUsers();
    }

    @Path("/groups")
    @GET
    public List<String> getGroups() {
        ArrayList<String> groups = new ArrayList<>(security.getGroups());
        groups.sort(Comparator.naturalOrder());
        return groups;
    }

    @Path("/check-wizard")
    @POST
    public boolean createApplication(WizardInfo wizard) {
        Table userTable = UpstairsAction.getTable(persistence, wizard.usersTable);
        if(userTable == null) {
            return true;
        }
        Column userPasswordColumn = UpstairsAction.getColumn(userTable, wizard.userPasswordProperty);
        if(userPasswordColumn == null) {
            return true;
        }
        if(userPasswordColumn.getActualJavaType() != String.class) {
            RequestMessages.addErrorMessage("The type of the password column, " + userPasswordColumn.getColumnName() + ", is not string: " + userPasswordColumn.getActualJavaType().getSimpleName());
            return false;
        }
        if(userPasswordColumn.getLength() < 32) { //TODO: would make sense to conditionalize this on the encryption algorithm + encoding combination
            RequestMessages.addErrorMessage("The length of the password column, " + userPasswordColumn.getColumnName() + ", is less than 32: " + userPasswordColumn.getLength());
            return false;
        }
        return true;
    }

}
