package com.ekp.agent;
import com.ekp.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final AgentService service;
    private final ExecutorService executor = new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    public AgentController(AgentService service) { this.service=service; }
    @PostMapping public AgentService.Result run(@Valid @RequestBody AgentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Future<AgentService.Result> task;
        try { task=executor.submit(() -> service.run(request,user)); }
        catch (RejectedExecutionException e) { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"All research workers are busy"); }
        try { return task.get(60,TimeUnit.SECONDS); }
        catch (TimeoutException e) { task.cancel(true); throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"Research timed out"); }
        catch (InterruptedException e) { task.cancel(true); Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Request interrupted"); }
        catch (ExecutionException e) { if (e.getCause() instanceof RuntimeException cause) throw cause; throw new IllegalStateException(e.getCause()); }
    }
    @PreDestroy void close() { executor.shutdownNow(); }
}
