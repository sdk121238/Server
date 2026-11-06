package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;

/** Add nullable demographic defaults without changing business versions or sync events. */
public class V3__backfill_patient_details extends BaseJavaMigration {
    /** 回填旧患者姓名、出生年月和可空字段，不修改业务版本、时间或同步事件。 */
    @Override public void migrate(Context context) throws Exception {
        ObjectMapper json=new ObjectMapper();
        try(var query=context.getConnection().prepareStatement("SELECT id,payload FROM patients");var rows=query.executeQuery();
            var update=context.getConnection().prepareStatement("UPDATE patients SET patient_name=?,birth_year_month=?,payload=? WHERE id=?")) {
            while(rows.next()) {
                ObjectNode data=(ObjectNode)json.readTree(rows.getString("payload"));
                String month=null;
                if(data.hasNonNull("birthDate")) month=LocalDate.parse(data.get("birthDate").asText()).toString().substring(0,7);
                for(String key:new String[]{"phoneNumber","gender","attendingDoctorId"}) if(!data.has(key)) data.putNull(key);
                if(month==null) data.putNull("birthYearMonth");else data.put("birthYearMonth",month);
                update.setString(1,data.path("name").asText());update.setString(2,month);update.setString(3,json.writeValueAsString(data));update.setString(4,rows.getString("id"));update.executeUpdate();
            }
        }
    }
}
