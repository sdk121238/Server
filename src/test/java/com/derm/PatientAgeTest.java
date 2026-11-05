package com.derm;
import com.derm.resource.PatientDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
class PatientAgeTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void completeBirthdayAndMonthOnlyHaveExplicitBoundaryRules() throws Exception {
        var birthday=json.readTree("{\"birthDate\":\"2000-11-06\",\"birthYearMonth\":\"2000-11\"}");
        assertThat(PatientDetails.age(birthday,LocalDate.of(2026,11,5))).isEqualTo(25);
        assertThat(PatientDetails.age(birthday,LocalDate.of(2026,11,6))).isEqualTo(26);
        var month=json.readTree("{\"birthYearMonth\":\"2000-11\"}");
        assertThat(PatientDetails.age(month,LocalDate.of(2026,10,31))).isEqualTo(25);
        assertThat(PatientDetails.age(month,LocalDate.of(2026,11,1))).isEqualTo(26);
        assertThat(PatientDetails.age(json.readTree("{}"),LocalDate.of(2026,11,5))).isNull();
        var leap=json.readTree("{\"birthDate\":\"2000-02-29\"}");
        assertThat(PatientDetails.age(leap,LocalDate.of(2025,2,28))).isEqualTo(24);
        assertThat(PatientDetails.age(leap,LocalDate.of(2025,3,1))).isEqualTo(25);
    }
}
