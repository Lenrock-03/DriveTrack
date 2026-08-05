package de.kornelriedl.drivetrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car
import de.kornelriedl.drivetrack.data.Trip
import de.kornelriedl.drivetrack.data.UserProfile
import de.kornelriedl.drivetrack.data.server.ServerApi
import de.kornelriedl.drivetrack.data.server.ServerAuthPreferences
import de.kornelriedl.drivetrack.data.server.ServerCrypto
import de.kornelriedl.drivetrack.data.server.ServerSession
import de.kornelriedl.drivetrack.export.BackupExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerBackupScreen(
    users: List<UserProfile>,
    cars: List<Car>,
    trips: List<Trip>,
    onImportComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loggedIn by remember { mutableStateOf(ServerAuthPreferences.isLoggedIn(context)) }
    var unlocked by remember { mutableStateOf(ServerSession.isUnlocked) }
    // "login" | "register" | "unlock" | "status"
    var mode by remember {
        mutableStateOf(if (!loggedIn) "login" else if (!unlocked) "unlock" else "status")
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server-Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            infoMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            when (mode) {
                "login" -> LoginForm(
                    isLoading = isLoading,
                    onSwitchToRegister = { mode = "register"; errorMessage = null },
                    onForgotPassword = { mode = "forgot"; errorMessage = null; infoMessage = null },
                    onLogin = { username, password ->
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { ServerApi.login(username, password) }
                            if (result.success && result.json != null) {
                                val token = result.json.getString("token")
                                val passwordSalt = result.json.getString("passwordSalt")
                                val dekWrappedPassword = result.json.getString("dekWrappedPassword")
                                try {
                                    val dek = withContext(Dispatchers.Default) {
                                        ServerCrypto.unwrapDek(
                                            ServerCrypto.EncryptedBlob.fromWrappedString(dekWrappedPassword),
                                            password,
                                            passwordSalt
                                        )
                                    }
                                    ServerSession.setDek(dek)
                                    ServerAuthPreferences.saveSession(
                                        context, token, username,
                                        ServerAuthPreferences.getEmail(context) ?: "",
                                        passwordSalt, dekWrappedPassword
                                    )
                                    loggedIn = true
                                    unlocked = true
                                    mode = "status"
                                } catch (e: Exception) {
                                    errorMessage = "Entschlüsselung fehlgeschlagen – falsches Passwort?"
                                }
                            } else {
                                errorMessage = result.error ?: "Login fehlgeschlagen"
                            }
                            isLoading = false
                        }
                    }
                )

                "register" -> RegisterForm(
                    isLoading = isLoading,
                    onSwitchToLogin = { mode = "login"; errorMessage = null },
                    onRegister = { username, email, password ->
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            val dek = ServerCrypto.randomDek()
                            val recoveryCode = ServerCrypto.randomRecoveryCode()
                            val passwordSalt = ServerCrypto.randomSaltBase64()
                            val recoverySalt = ServerCrypto.randomSaltBase64()

                            val (dekWrappedPassword, dekWrappedRecovery) = withContext(Dispatchers.Default) {
                                ServerCrypto.wrapDek(dek, password, passwordSalt).toWrappedString() to
                                    ServerCrypto.wrapDek(dek, recoveryCode, recoverySalt).toWrappedString()
                            }

                            val result = withContext(Dispatchers.IO) {
                                ServerApi.register(
                                    username, email, password,
                                    dekWrappedPassword, dekWrappedRecovery,
                                    passwordSalt, recoverySalt, recoveryCode
                                )
                            }

                            if (result.success) {
                                infoMessage = "Konto angelegt! Dein Recovery-Code wurde an $email geschickt " +
                                    "– bewahr die Mail gut auf, falls du dein Passwort mal vergisst."
                                ServerSession.setDek(dek)
                                mode = "login"
                            } else {
                                errorMessage = result.error ?: "Registrierung fehlgeschlagen"
                            }
                            isLoading = false
                        }
                    }
                )

                "forgot" -> ForgotPasswordForm(
                    isLoading = isLoading,
                    onBackToLogin = { mode = "login"; errorMessage = null; infoMessage = null },
                    onRequestCode = { email ->
                        scope.launch {
                            withContext(Dispatchers.IO) { ServerApi.requestReset(email) }
                            infoMessage = "Falls diese E-Mail registriert ist, wurde ein Bestätigungscode verschickt."
                        }
                    },
                    onReset = { email, code, recoveryCode, newPassword ->
                        errorMessage = null
                        infoMessage = null
                        isLoading = true
                        scope.launch {
                            val verifyResult = withContext(Dispatchers.IO) { ServerApi.verifyResetCode(email, code) }
                            if (!verifyResult.success || verifyResult.json == null) {
                                errorMessage = verifyResult.error ?: "Code ungültig oder abgelaufen"
                                isLoading = false
                                return@launch
                            }
                            try {
                                val recoverySalt = verifyResult.json.getString("recoverySalt")
                                val dekWrappedRecovery = verifyResult.json.getString("dekWrappedRecovery")
                                val recoveredDek = withContext(Dispatchers.Default) {
                                    ServerCrypto.unwrapDek(
                                        ServerCrypto.EncryptedBlob.fromWrappedString(dekWrappedRecovery),
                                        recoveryCode,
                                        recoverySalt
                                    )
                                }
                                val newPasswordSalt = ServerCrypto.randomSaltBase64()
                                val newDekWrappedPassword = withContext(Dispatchers.Default) {
                                    ServerCrypto.wrapDek(recoveredDek, newPassword, newPasswordSalt).toWrappedString()
                                }
                                val confirmResult = withContext(Dispatchers.IO) {
                                    ServerApi.confirmReset(email, code, newPassword, newDekWrappedPassword, newPasswordSalt)
                                }
                                if (confirmResult.success) {
                                    infoMessage = "Passwort erfolgreich zurückgesetzt. Du kannst dich jetzt einloggen."
                                    mode = "login"
                                } else {
                                    errorMessage = confirmResult.error ?: "Zurücksetzen fehlgeschlagen"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Recovery-Code falsch"
                            }
                            isLoading = false
                        }
                    }
                )

                "unlock" -> UnlockForm(
                    username = ServerAuthPreferences.getUsername(context) ?: "",
                    isLoading = isLoading,
                    onLogout = {
                        ServerAuthPreferences.clearSession(context)
                        ServerSession.clear()
                        loggedIn = false
                        unlocked = false
                        mode = "login"
                    },
                    onUnlock = { password ->
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            val passwordSalt = ServerAuthPreferences.getPasswordSalt(context)
                            val wrapped = ServerAuthPreferences.getDekWrappedPassword(context)
                            if (passwordSalt == null || wrapped == null) {
                                errorMessage = "Sitzung ungültig, bitte neu einloggen"
                                mode = "login"
                                isLoading = false
                                return@launch
                            }
                            try {
                                val dek = withContext(Dispatchers.Default) {
                                    ServerCrypto.unwrapDek(
                                        ServerCrypto.EncryptedBlob.fromWrappedString(wrapped),
                                        password,
                                        passwordSalt
                                    )
                                }
                                ServerSession.setDek(dek)
                                unlocked = true
                                mode = "status"
                            } catch (e: Exception) {
                                errorMessage = "Falsches Passwort"
                            }
                            isLoading = false
                        }
                    }
                )

                else -> StatusView(
                    username = ServerAuthPreferences.getUsername(context) ?: "",
                    isLoading = isLoading,
                    onBackup = {
                        errorMessage = null
                        infoMessage = null
                        isLoading = true
                        scope.launch {
                            val token = ServerAuthPreferences.getToken(context)
                            val dek = ServerSession.dek
                            if (token == null || dek == null) {
                                errorMessage = "Bitte erst einloggen/entsperren"
                                isLoading = false
                                return@launch
                            }
                            val json = BackupExporter.buildBackupJson(context, users, cars, trips)
                            val blob = withContext(Dispatchers.Default) {
                                ServerCrypto.encryptWithDek(json, dek)
                            }
                            val result = withContext(Dispatchers.IO) {
                                ServerApi.uploadBackup(token, blob.ciphertextBase64, blob.ivBase64)
                            }
                            infoMessage = if (result.success) "Backup erfolgreich auf dem Server gesichert!" else null
                            errorMessage = if (!result.success) (result.error ?: "Sichern fehlgeschlagen") else null
                            isLoading = false
                        }
                    },
                    onRestore = {
                        errorMessage = null
                        infoMessage = null
                        isLoading = true
                        scope.launch {
                            val token = ServerAuthPreferences.getToken(context)
                            val dek = ServerSession.dek
                            if (token == null || dek == null) {
                                errorMessage = "Bitte erst einloggen/entsperren"
                                isLoading = false
                                return@launch
                            }
                            val result = withContext(Dispatchers.IO) { ServerApi.downloadLatestBackup(token) }
                            if (result.success && result.json != null) {
                                try {
                                    val blob = ServerCrypto.EncryptedBlob(
                                        ciphertextBase64 = result.json.getString("ciphertext"),
                                        ivBase64 = result.json.getString("iv")
                                    )
                                    val json = withContext(Dispatchers.Default) {
                                        ServerCrypto.decryptWithDek(blob, dek)
                                    }
                                    val summary = withContext(Dispatchers.IO) {
                                        BackupExporter.importBackupFromJson(context, json)
                                    }
                                    infoMessage = if (summary.success) {
                                        "${summary.tripsImported} Fahrt(en) wiederhergestellt" +
                                            if (summary.tripsSkipped > 0) ", ${summary.tripsSkipped} Duplikate übersprungen" else ""
                                    } else null
                                    if (summary.success) onImportComplete()
                                } catch (e: Exception) {
                                    errorMessage = "Entschlüsseln fehlgeschlagen"
                                }
                            } else {
                                errorMessage = result.error ?: "Kein Backup auf dem Server gefunden"
                            }
                            isLoading = false
                        }
                    },
                    onLogout = {
                        ServerAuthPreferences.clearSession(context)
                        ServerSession.clear()
                        loggedIn = false
                        unlocked = false
                        mode = "login"
                        errorMessage = null
                        infoMessage = null
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    isLoading: Boolean,
    onSwitchToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text("Einloggen", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = username, onValueChange = { username = it },
        label = { Text("Benutzername") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text("Passwort") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onLogin(username.trim(), password) },
        enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Einloggen")
    }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onSwitchToRegister, modifier = Modifier.fillMaxWidth()) {
        Text("Noch kein Konto? Jetzt registrieren")
    }
    TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
        Text("Passwort vergessen?")
    }
}

@Composable
private fun RegisterForm(
    isLoading: Boolean,
    onSwitchToLogin: () -> Unit,
    onRegister: (username: String, email: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Text("Registrieren", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        "Dein Backup wird Ende-zu-Ende-verschlüsselt – nicht mal wir können es lesen. " +
            "Du bekommst per Mail einen Recovery-Code, den solltest du gut aufbewahren.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = username, onValueChange = { username = it },
        label = { Text("Benutzername") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = email, onValueChange = { email = it },
        label = { Text("E-Mail") }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text("Passwort") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = passwordConfirm, onValueChange = { passwordConfirm = it },
        label = { Text("Passwort wiederholen") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
        Text(
            "Passwörter stimmen nicht überein",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onRegister(username.trim(), email.trim(), password) },
        enabled = !isLoading && username.isNotBlank() && email.isNotBlank() &&
            password.length >= 8 && password == passwordConfirm,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Konto erstellen")
    }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onSwitchToLogin, modifier = Modifier.fillMaxWidth()) {
        Text("Schon ein Konto? Einloggen")
    }
}

@Composable
private fun ForgotPasswordForm(
    isLoading: Boolean,
    onBackToLogin: () -> Unit,
    onRequestCode: (email: String) -> Unit,
    onReset: (email: String, code: String, recoveryCode: String, newPassword: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }

    Text("Passwort vergessen", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))

    if (!codeRequested) {
        Text(
            "Gib deine E-Mail-Adresse ein - du bekommst einen Bestätigungscode zugeschickt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("E-Mail") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onRequestCode(email.trim())
                codeRequested = true
            },
            enabled = !isLoading && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Code anfordern")
        }
    } else {
        Text(
            "Trag den Code aus der E-Mail ein, dazu deinen Recovery-Code (aus der Mail von der " +
                "Registrierung) und dein neues Passwort.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Bestätigungscode aus der Mail") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = recoveryCode, onValueChange = { recoveryCode = it },
            label = { Text("Recovery-Code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPassword, onValueChange = { newPassword = it },
            label = { Text("Neues Passwort") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPasswordConfirm, onValueChange = { newPasswordConfirm = it },
            label = { Text("Neues Passwort wiederholen") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (newPasswordConfirm.isNotEmpty() && newPassword != newPasswordConfirm) {
            Text(
                "Passwörter stimmen nicht überein",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onReset(email.trim(), code.trim(), recoveryCode.trim(), newPassword) },
            enabled = !isLoading && code.isNotBlank() && recoveryCode.isNotBlank() &&
                newPassword.length >= 8 && newPassword == newPasswordConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Passwort zurücksetzen")
        }
    }

    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
        Text("Zurück zum Login")
    }
}

@Composable
private fun UnlockForm(
    username: String,
    isLoading: Boolean,
    onLogout: () -> Unit,
    onUnlock: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    Text("Wieder freischalten", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        "Eingeloggt als \"$username\". Gib dein Passwort erneut ein, um deine Backups zu entschlüsseln.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text("Passwort") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onUnlock(password) },
        enabled = !isLoading && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Entsperren")
    }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text("Abmelden")
    }
}

@Composable
private fun StatusView(
    username: String,
    isLoading: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Eingeloggt als \"$username\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Backups sind Ende-zu-Ende-verschlüsselt gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onBackup,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text("Backup auf Server sichern")
    }

    Spacer(Modifier.height(10.dp))

    OutlinedButton(
        onClick = onRestore,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Vom Server wiederherstellen")
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onLogout) {
            Text("Abmelden", color = MaterialTheme.colorScheme.error)
        }
    }
}
