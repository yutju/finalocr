package com.example.myapplication2222;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileManagementActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText editTextName;
    private EditText editTextEmail;
    private EditText editTextCurrentPassword;
    private EditText editTextNewPassword;
    private EditText editTextConfirmNewPassword;
    private EditText editTextIssueDate; // 발급일자 입력 필드 추가
    private TextView textViewSSN;
    private Button saveButton;
    private Button deleteAccountButton; // 회원 탈퇴 버튼 추가

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_management);

        // Firebase 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        editTextName = findViewById(R.id.editTextName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextCurrentPassword = findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmNewPassword = findViewById(R.id.editTextConfirmNewPassword);
        editTextIssueDate = findViewById(R.id.editTextIssueDate);
        textViewSSN = findViewById(R.id.textViewSSN);
        saveButton = findViewById(R.id.save_button);
        deleteAccountButton = findViewById(R.id.delete_account_button); // 회원 탈퇴 버튼 초기화

        // 현재 사용자 정보 로드
        loadUserProfile();

        // 저장 버튼 클릭 리스너 설정
        saveButton.setOnClickListener(v -> saveUserProfile());

        // 회원 탈퇴 버튼 클릭 리스너 설정
        deleteAccountButton.setOnClickListener(v -> deleteUserAccount());
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // 현재 사용자 이메일 설정
            editTextEmail.setText(currentUser.getEmail());

            // Firestore에서 사용자 이름, 주민등록번호, 발급일자 로드
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            String ssn = documentSnapshot.getString("ssn");
                            String issueDate = documentSnapshot.getString("issueDate");
                            editTextName.setText(name);
                            textViewSSN.setText("주민등록번호: " + ssn);
                            editTextIssueDate.setText(issueDate);
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(ProfileManagementActivity.this, "프로필 정보를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());
        }
    }

    private void saveUserProfile() {
        final String name = editTextName.getText().toString().trim();
        final String currentPassword = editTextCurrentPassword.getText().toString().trim();
        final String newPassword = editTextNewPassword.getText().toString().trim();
        final String confirmNewPassword = editTextConfirmNewPassword.getText().toString().trim();
        String issueDateInput = editTextIssueDate.getText().toString().trim();

        // 입력값 유효성 검사
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmNewPassword)) {
            Toast.makeText(this, "모든 비밀번호 필드를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(this, "새 비밀번호가 서로 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 발급일자 형식 설정 및 검증
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        final String formattedIssueDate;
        try {
            Date date = dateFormat.parse(issueDateInput); // 입력값을 날짜로 변환
            formattedIssueDate = dateFormat.format(date); // 형식이 맞는 경우 다시 설정
        } catch (ParseException e) {
            Toast.makeText(this, "발급일자를 yyyy.MM.dd 형식으로 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);

            // 현재 비밀번호 인증
            currentUser.reauthenticate(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // 비밀번호 변경
                    currentUser.updatePassword(newPassword).addOnCompleteListener(passwordTask -> {
                        if (passwordTask.isSuccessful()) {
                            // Firestore에 사용자 이름 및 발급일자 저장
                            Map<String, Object> userUpdates = new HashMap<>();
                            userUpdates.put("name", name);
                            userUpdates.put("issueDate", formattedIssueDate); // 형식화된 발급일자 저장

                            db.collection("users").document(currentUser.getUid())
                                    .set(userUpdates)
                                    .addOnSuccessListener(aVoid -> Toast.makeText(ProfileManagementActivity.this, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(ProfileManagementActivity.this, "프로필 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show());
                        } else {
                            Toast.makeText(ProfileManagementActivity.this, "비밀번호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(ProfileManagementActivity.this, "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void deleteUserAccount() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Firestore에서 사용자 데이터 삭제
            db.collection("users").document(currentUser.getUid()).delete()
                    .addOnSuccessListener(aVoid -> {
                        // Firebase Auth에서 사용자 삭제
                        currentUser.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(ProfileManagementActivity.this, "계정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                finish(); // Activity 종료
                            } else {
                                Toast.makeText(ProfileManagementActivity.this, "계정 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .addOnFailureListener(e -> Toast.makeText(ProfileManagementActivity.this, "사용자 데이터 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show());
        }
    }
}
