package com.janmeterai.service.janmeteraiservice.service;

import com.janmeterai.service.janmeteraiservice.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class ConstituencyAddressMatcher {
    private final JdbcTemplate jdbc;
    public ConstituencyAddressMatcher(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public ConstituencyMatchResultEvent match(ConstituencyMatchEvent event){
        List<Candidate> candidates=matchViaVillage(event);
        String matchedBy="VILLAGE_LOCAL_BODY";
        if(candidates.isEmpty()){candidates=matchViaLocalBodyName(event);matchedBy="LOCAL_BODY_NAME";}
        Map<String,Candidate> unique=new LinkedHashMap<>();
        for(Candidate candidate:candidates){if(candidate.assemblyId()!=null&&candidate.parliamentId()!=null)unique.put(candidate.assemblyId()+":"+candidate.parliamentId(),candidate);}
        if(unique.size()==1){Candidate c=unique.values().iterator().next();return result(event,"MATCHED",c.assemblyId(),c.parliamentId(),candidates.size()==1?1.0:.92,matchedBy,"Matched Aadhaar locality to the constituency coverage hierarchy.");}
        if(unique.size()>1)return result(event,"UNRESOLVED",null,null,.55,matchedBy,"The address belongs to a local body shared by multiple constituencies. Please select the correct constituency.");
        return result(event,"UNRESOLVED",null,null,0.0,"NO_MATCH","No reliable constituency mapping was found for the Aadhaar address. Please select it manually.");
    }

    private List<Candidate> matchViaVillage(ConstituencyMatchEvent event){
        String place=first(event.villageTownCity(),event.locality());if(place==null)return List.of();
        return jdbc.query("""
          select distinct m.assembly_constituency_id,m.parliament_constituency_id
          from janmeter_village_city v
          join janmeter_local_body lb on lb.local_body_code=v.local_body_code
          join janmeter_constituency_local_body_mapping m on m.local_body_id=lb.id
          where lower(trim(v.name))=lower(trim(?))
            and (? is null or lower(trim(v.state_name_english))=lower(trim(?)))
            and (? is null or lower(trim(v.district_name_english))=lower(trim(?)))
            and (? is null or lower(trim(v.subdistrict_name_english))=lower(trim(?)))
            and m.mapping_status='MAPPED'
          """,(rs,row)->new Candidate((Long)rs.getObject(1),(Long)rs.getObject(2)),place,event.state(),event.state(),event.district(),event.district(),event.subDistrict(),event.subDistrict());
    }
    private List<Candidate> matchViaLocalBodyName(ConstituencyMatchEvent event){
        String place=first(event.villageTownCity(),event.locality(),event.postOffice());if(place==null)return List.of();
        return jdbc.query("""
          select distinct m.assembly_constituency_id,m.parliament_constituency_id
          from janmeter_local_body lb
          join janmeter_constituency_local_body_mapping m on m.local_body_id=lb.id
          join janmeter_state s on s.code=lb.state_code
          where (lower(trim(lb.local_body_name_english))=lower(trim(?)) or lower(trim(m.local_government_name))=lower(trim(?)))
            and (? is null or lower(trim(s.statename))=lower(trim(?)))
            and m.mapping_status='MAPPED'
          """,(rs,row)->new Candidate((Long)rs.getObject(1),(Long)rs.getObject(2)),place,place,event.state(),event.state());
    }
    private ConstituencyMatchResultEvent result(ConstituencyMatchEvent e,String status,Long assembly,Long parliament,double confidence,String by,String message){return new ConstituencyMatchResultEvent(UUID.randomUUID().toString(),e.eventId(),Instant.now(),e.userId(),status,assembly,parliament,confidence,by,message,e.correlationId());}
    private String first(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return null;}
    private record Candidate(Long assemblyId,Long parliamentId){}
}
