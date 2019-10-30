package com.dopplertask.doppler.controller;

import com.dopplertask.doppler.domain.Task;
import com.dopplertask.doppler.domain.TaskExecution;
import com.dopplertask.doppler.domain.TaskExecutionLog;
import com.dopplertask.doppler.domain.action.Action;
import com.dopplertask.doppler.dto.LoginParameters;
import com.dopplertask.doppler.dto.SimpleChecksumResponseDto;
import com.dopplertask.doppler.dto.SimpleIdResponseDto;
import com.dopplertask.doppler.dto.SimpleMessageResponseDTO;
import com.dopplertask.doppler.dto.TaskCreationDTO;
import com.dopplertask.doppler.dto.TaskExecutionDTO;
import com.dopplertask.doppler.dto.TaskExecutionListDTO;
import com.dopplertask.doppler.dto.TaskExecutionLogResponseDTO;
import com.dopplertask.doppler.dto.TaskNameDTO;
import com.dopplertask.doppler.dto.TaskRequestDTO;
import com.dopplertask.doppler.dto.TaskResponseSingleDTO;
import com.dopplertask.doppler.service.ExecutionService;
import com.dopplertask.doppler.service.TaskRequest;
import com.dopplertask.doppler.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ExecutionService executionService;

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @PostMapping(path = "/schedule/task")
    public ResponseEntity<SimpleIdResponseDto> scheduleTask(@RequestBody TaskRequestDTO taskRequestDTO) {
        TaskRequest request = new TaskRequest(taskRequestDTO.getTaskName(), taskRequestDTO.getParameters());
        request.setChecksum(taskRequestDTO.getTaskName());
        TaskExecution taskExecution = taskService.delegate(request);

        if (taskExecution != null) {
            SimpleIdResponseDto idResponseDto = new SimpleIdResponseDto();
            idResponseDto.setId(String.valueOf(taskExecution.getId()));
            return new ResponseEntity<>(idResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/schedule/directtask")
    public ResponseEntity<TaskExecutionLogResponseDTO> runTask(@RequestBody TaskRequestDTO taskRequestDTO) {
        TaskRequest request = new TaskRequest(taskRequestDTO.getTaskName(), taskRequestDTO.getParameters());
        request.setChecksum(taskRequestDTO.getTaskName());
        TaskExecution execution = taskService.runRequest(request);

        TaskExecutionLogResponseDTO responseDTO = new TaskExecutionLogResponseDTO();
        if (execution != null) {
            for (TaskExecutionLog log : execution.getLogs()) {
                responseDTO.getOutput().add(log.getOutput());
            }

            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

    }

    @PostMapping(path = "/task/push")
    public ResponseEntity<TaskNameDTO> pushTask(@RequestBody TaskNameDTO taskNameDTO) {
        boolean pushed = taskService.pushTask(taskNameDTO.getTaskName());

        if (pushed) {
            return new ResponseEntity<>(taskNameDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/task")
    public ResponseEntity<SimpleChecksumResponseDto> createTask(@RequestBody String body) throws IOException, NoSuchAlgorithmException {

        // Translate JSON to object
        ObjectMapper mapper = new ObjectMapper();
        TaskCreationDTO taskCreationDTO = mapper.readValue(body, TaskCreationDTO.class);

        // Generate compact JSON
        String compactJSON = mapper.writeValueAsString(taskCreationDTO);

        // Create checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(compactJSON.getBytes(StandardCharsets.UTF_8));
        String sha3_256hex = bytesToHex(encodedhash);

        List<Action> actions = taskCreationDTO.getActions();

        Long id = taskService.createTask(taskCreationDTO.getName(), actions, taskCreationDTO.getDescription(), sha3_256hex);

        if (id != null) {
            SimpleChecksumResponseDto checksumResponseDto = new SimpleChecksumResponseDto();
            checksumResponseDto.setChecksum(sha3_256hex);
            return new ResponseEntity<>(checksumResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/task")
    public ResponseEntity<List<TaskResponseSingleDTO>> getTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskResponseSingleDTO> taskResponseDTOList = new ArrayList<>();

        for (Task task : tasks) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setChecksum(task.getChecksum());
            taskDto.setName(task.getName());
            taskDto.setCreated(task.getCreated());

            taskResponseDTOList.add(taskDto);
        }

        return new ResponseEntity<>(taskResponseDTOList, HttpStatus.OK);
    }

    @GetMapping("/task/detail")
    public ResponseEntity<List<TaskResponseSingleDTO>> getDetailedTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskResponseSingleDTO> taskResponseDTOList = new ArrayList<>();

        for (Task task : tasks) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setChecksum(task.getChecksum());
            taskDto.setName(task.getName());
            taskDto.setCreated(task.getCreated());
            taskDto.setActions(task.getActionList());

            taskResponseDTOList.add(taskDto);
        }

        return new ResponseEntity<>(taskResponseDTOList, HttpStatus.OK);
    }

    @GetMapping("/task/{id}")
    public ResponseEntity<TaskResponseSingleDTO> getTask(@PathVariable("id") long id) {
        Task task = taskService.getTask(id);
        if (task != null) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setName(task.getName());
            taskDto.setDescription(task.getDescription());
            taskDto.setActions(task.getActionList());
            taskDto.setChecksum(task.getChecksum());

            return new ResponseEntity<>(taskDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/task/download")
    public ResponseEntity<SimpleChecksumResponseDto> pullTask(@RequestParam("taskName") String taskName) {
        Optional<Task> task = executionService.pullTask(taskName, taskService);
        if (task.isPresent()) {
            SimpleChecksumResponseDto checksumDto = new SimpleChecksumResponseDto();
            checksumDto.setChecksum(task.get().getChecksum());

            return new ResponseEntity<>(checksumDto, HttpStatus.OK);
        }
        SimpleChecksumResponseDto checksumResponseDto = new SimpleChecksumResponseDto();
        checksumResponseDto.setChecksum("Did not find task.");
        return new ResponseEntity<>(checksumResponseDto, HttpStatus.NOT_FOUND);
    }

    @GetMapping("/executions")
    public ResponseEntity<TaskExecutionListDTO> getExecutions() {
        List<TaskExecution> executions = taskService.getExecutions();
        List<TaskExecutionDTO> executionDTOList = new ArrayList<>();
        executions.forEach(item -> {
            TaskExecutionDTO dto = new TaskExecutionDTO();
            dto.setStatus(item.getStatus().name());
            dto.setStartDate(item.getStartdate());
            dto.setEndDate(item.getEnddate());
            dto.setExecutionId(Long.toString(item.getId()));
            dto.setTaskName(item.getTask().getName() != null ? item.getTask().getName() : Long.toString(item.getTask().getId()));
            executionDTOList.add(dto);
        });

        TaskExecutionListDTO executionListDTO = new TaskExecutionListDTO();
        executionListDTO.setExecutions(executionDTOList);

        return new ResponseEntity<>(executionListDTO, HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<SimpleMessageResponseDTO> login(@RequestBody LoginParameters loginParameters) {

        boolean loggedIn = taskService.loginUser(loginParameters.getUsername(), loginParameters.getPassword());

        if (!loggedIn) {
            return new ResponseEntity<>(new SimpleMessageResponseDTO("Could not login. Check your credentials."), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(new SimpleMessageResponseDTO("Successfully logged in"), HttpStatus.OK);
    }

    @DeleteMapping("/task/{taskNameOrChecksum}")
    public ResponseEntity<SimpleMessageResponseDTO> deleteTask(@PathVariable String taskNameOrChecksum) {

        Task task = taskService.deleteTask(taskNameOrChecksum);

        if (task != null) {
            SimpleMessageResponseDTO messageDto = new SimpleMessageResponseDTO("Task has been deleted " + task.getChecksum());

            return new ResponseEntity<>(messageDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(new SimpleMessageResponseDTO("Could not delete task"), HttpStatus.BAD_REQUEST);
    }
}