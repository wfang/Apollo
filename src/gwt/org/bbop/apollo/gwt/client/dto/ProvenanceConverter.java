package org.bbop.apollo.gwt.client.dto;

import com.google.gwt.json.client.*;
import org.bbop.apollo.gwt.shared.provenance.Provenance;
import org.bbop.apollo.gwt.shared.provenance.Reference;
import org.bbop.apollo.gwt.shared.provenance.WithOrFrom;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ndunn on 3/31/15.
 */
public class ProvenanceConverter {

  public static Provenance convertFromJson(JSONObject object) {
    Provenance provenance = new Provenance();

//                    "geneRelationship":"RO:0002326", "goTerm":"GO:0031084", "references":"[\"ref:12312\"]", "gene":
//                    "1743ae6c-9a37-4a41-9b54-345065726d5f", "negate":false, "evidenceCode":"ECO:0000205", "withOrFrom":
//                    "[\"adf:12312\"]"
    provenance.setId(Math.round(object.get("id").isNumber().doubleValue()));
    provenance.setFeature(object.get("gene").isString().stringValue());
    provenance.setField(object.get("field").isString().stringValue());
    if(object.containsKey("evidenceCodeLabel")){
      provenance.setEvidenceCodeLabel(object.get("evidenceCodeLabel").isString().stringValue());
    }
    provenance.setEvidenceCode(object.get("evidenceCode").isString().stringValue());
    provenance.setReference(new Reference(object.get("reference").isString().stringValue()));


    List<WithOrFrom> withOrFromList = new ArrayList<>();
    if (object.get("withOrFrom").isString() != null) {
      String withOrFromString = object.get("withOrFrom").isString().stringValue();
      JSONArray withOrFromArray = JSONParser.parseStrict(withOrFromString).isArray();
      for (int i = 0; i < withOrFromArray.size(); i++) {
        WithOrFrom withOrFrom = new WithOrFrom(withOrFromArray.get(i).isString().stringValue());
        withOrFromList.add(withOrFrom);
      }
    }
    provenance.setWithOrFromList(withOrFromList);

    return provenance;
  }

  public static JSONObject convertToJson(Provenance provenance) {
    JSONObject object = new JSONObject();

    // TODO: an NPE in here, somehwere
    if (provenance.getId() != null) {
      object.put("id", new JSONNumber(provenance.getId()));
    }
    object.put("gene", new JSONString(provenance.getFeature()));
    object.put("field", new JSONString(provenance.getField()));
    object.put("evidenceCode", new JSONString(provenance.getEvidenceCode()));
    object.put("evidenceCodeLabel", new JSONString(provenance.getEvidenceCodeLabel()));
    object.put("reference", new JSONString(provenance.getReference().getReferenceString()));

    // TODO: finish this
    JSONArray withArray = new JSONArray();

    for (WithOrFrom withOrFrom : provenance.getWithOrFromList()) {
      withArray.set(withArray.size(), new JSONString(withOrFrom.getDisplay()));
    }

    object.put("withOrFrom", withArray);

    return object;
  }
}
