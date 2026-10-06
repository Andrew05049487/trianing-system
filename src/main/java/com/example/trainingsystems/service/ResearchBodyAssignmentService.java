package com.example.trainingsystems.service;

import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Uses persisted assignment IDs, never Flutter list indices or client therapist IDs. */
@Service
public class ResearchBodyAssignmentService {
    private final ExerciseAssignmentRepository assignments;
    private final UserBindingRepository bindings;
    public ResearchBodyAssignmentService(ExerciseAssignmentRepository assignments,UserBindingRepository bindings){
        this.assignments=assignments;this.bindings=bindings;
    }
    public void requireAssigned(Long patientId,JsonNode payload){
        // CUSTOM has no persisted action-definition mapping yet. Fail closed;
        // its therapist-authored rotations cannot be silently assigned this schema.
        if(!"DEFAULT".equals(payload.path("exerciseType").asText())) deny();
        long exerciseId;
        try{exerciseId=Long.parseLong(payload.path("exerciseId").asText());}
        catch(NumberFormatException e){throw denied();}
        var assignment=assignments.findByExercise_IdAndPatient_Id(exerciseId,patientId).orElseThrow(ResearchBodyAssignmentService::denied);
        if(!assignment.isActive() || assignment.getExercise()==null ||
            !"站姿抬腳式訓練".equals(assignment.getExercise().getExerciseName()) ||
            assignment.getAssignedByTherapist()==null ||
            !bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(patientId,assignment.getAssignedByTherapist().getId(),"THERAPIST")) deny();
    }
    private static void deny(){throw denied();}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"BODY_RESEARCH_ASSIGNMENT_REQUIRED");}
}
