package blog.code.codeblog.service;

import blog.code.codeblog.command.user.CreateUserCommand;
import blog.code.codeblog.command.user.LoginCommand;
import blog.code.codeblog.dto.authentication.LoginResponseDTO;
import blog.code.codeblog.dto.authentication.RegisterResponseDTO;
import blog.code.codeblog.dto.authentication.VerifyOTPrequestDTO;
import blog.code.codeblog.enums.AuthFlow;
import blog.code.codeblog.model.User;
import blog.code.codeblog.service.integration.Auth0ServiceIntergration;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class AuthenticationService {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private Auth0ServiceIntergration auth0ServiceIntergration;


    public RegisterResponseDTO register(CreateUserCommand cmd) {
        log.info("[register] register solicitation received for user user={}",  cmd.getEmail());

        if (userService.findByLogin(cmd.getEmail()) != null) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (cmd.getFlow() == AuthFlow.OTP) {
            log.info("[register] OTP flow detected. Verifying OTP for user: {}", cmd.getEmail());
            auth0ServiceIntergration.verifyOTP(new VerifyOTPrequestDTO(cmd.getEmail(), cmd.getCredential()));
        }
        log.info("[register] Generating token for user: {}", cmd.getEmail());
        User newUser = userService.saveUser(cmd);
        String token = tokenService.generateToken(newUser);
        return new RegisterResponseDTO(token);
    }

    public LoginResponseDTO login(LoginCommand cmd) {
        log.info("[login] login solicitation received for user use flow={} user={}", cmd.getFlow(), cmd.getLogin());
        User user = switch (cmd.getFlow()) {
            case OTP      -> authenticateWithOtp(cmd);
            case PASSWORD -> authenticateWithPassword(cmd);
        };
        return new LoginResponseDTO(tokenService.generateToken(user));
    }

    private User authenticateWithOtp(LoginCommand cmd) {
        auth0ServiceIntergration.verifyOTP(new VerifyOTPrequestDTO(cmd.getLogin(), cmd.getCredential()));
        return userService.findByLogin(cmd.getLogin());
    }

    private User authenticateWithPassword(LoginCommand cmd) {
        var token = new UsernamePasswordAuthenticationToken(cmd.getLogin(), cmd.getCredential());
        return (User) authenticationManager.authenticate(token).getPrincipal();
    }

    public void logout(HttpServletRequest request) {
        String recoveredToken = tokenService.recoverToken(request);
        tokenService.blackListToken(recoveredToken);

        log.info("[logout] User logged out successfully. Remote user: {}", request.getRemoteUser());
    }
}
