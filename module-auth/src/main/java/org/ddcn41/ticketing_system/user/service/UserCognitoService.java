package org.ddcn41.ticketing_system.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Service
@RequiredArgsConstructor
public class UserCognitoService {
    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${auth.cognito.user-pool-id}")
    private String userPoolId;

    public String createCognitoUser(String username, String email, String name) {
        AdminCreateUserRequest cognitoRequest = AdminCreateUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("name").value(name).build()
                )
                .messageAction(MessageActionType.SUPPRESS)
                .build();

        AdminCreateUserResponse cognitoResponse = cognitoClient.adminCreateUser(cognitoRequest);
        
        return cognitoResponse.user().attributes().stream()
                .filter(attr -> attr.name().equals("sub"))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    public void deleteCognitoUser(String username) {
        AdminDeleteUserRequest cognitoRequest = AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build();

        cognitoClient.adminDeleteUser(cognitoRequest);
    }

    public void changePassword(String username, String password) {
        AdminSetUserPasswordRequest request = AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .password(password)
                .permanent(true)
                .build();

        cognitoClient.adminSetUserPassword(request);
    }

    public void addUserToGroup(String username, String groupName) {
        groupName = groupName.toLowerCase();

        AdminAddUserToGroupRequest request = AdminAddUserToGroupRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .groupName(groupName)
                .build();

        cognitoClient.adminAddUserToGroup(request);
    }
}
