import '../../network/dio_client.dart';
import '../../network/api_endpoints.dart';
import '../models/user_model.dart';

// ============================================================
// VOYA — Auth Repository
// lib/data/repositories/auth_repository.dart
//
// Wraps every auth-related API call. Screens/providers should
// talk to THIS class, never call DioClient directly for auth.
// Throws ApiException on failure (see network/dio_client.dart) —
// catch that in your UI layer to show error messages.
// ============================================================

class AuthRepository {
  final DioClient _client;

  AuthRepository({DioClient? client}) : _client = client ?? DioClient();

  /// Step 1 — send an OTP to the given local phone number
  /// (e.g. "3001234567", without country code).
  /// Returns true if the OTP was sent successfully.
  Future<bool> sendOtp(String phoneNumber) async {
    final response = await _client.post(
      ApiEndpoints.sendOtp,
      data: {'phone_number': phoneNumber},
    );
    return response.statusCode == 200 || response.statusCode == 201;
  }

  /// Step 2 — verify the OTP the user entered.
  /// On success, saves the auth token and returns the logged-in user.
  /// If the phone number is new, the backend is expected to still
  /// return a user object (with isPhoneVerified true, profile fields
  /// empty) so the UI can route to "create account" instead of home.
  Future<UserModel> verifyOtp({
    required String phoneNumber,
    required String otp,
  }) async {
    final response = await _client.post(
      ApiEndpoints.verifyOtp,
      data: {
        'phone_number': phoneNumber,
        'otp': otp,
      },
    );

    final data = response.data as Map<String, dynamic>;
    final token = data['token'] as String?;
    if (token != null && token.isNotEmpty) {
      await _client.saveAuthToken(token);
    }

    return UserModel.fromJson(data['user'] as Map<String, dynamic>);
  }

  /// Resend OTP to the same phone number (e.g. after countdown ends).
  Future<bool> resendOtp(String phoneNumber) async {
    final response = await _client.post(
      ApiEndpoints.resendOtp,
      data: {'phone_number': phoneNumber},
    );
    return response.statusCode == 200 || response.statusCode == 201;
  }

  /// Complete signup for a new user after phone verification —
  /// collects name/email and finalizes account creation.
  Future<UserModel> completeRegistration({
    required String fullName,
    String? email,
  }) async {
    final response = await _client.post(
      ApiEndpoints.register,
      data: {
        'full_name': fullName,
        if (email != null && email.isNotEmpty) 'email': email,
      },
    );

    final data = response.data as Map<String, dynamic>;
    return UserModel.fromJson(data['user'] as Map<String, dynamic>);
  }

  /// Fetch the currently logged-in user's profile.
  /// Call this on app start (if a token is stored) to restore session.
  Future<UserModel> getCurrentUser() async {
    final response = await _client.get(ApiEndpoints.profile);
    final data = response.data as Map<String, dynamic>;
    return UserModel.fromJson(data['user'] as Map<String, dynamic>? ?? data);
  }

  /// Update profile fields (name, email, avatar URL, etc).
  Future<UserModel> updateProfile(Map<String, dynamic> fields) async {
    final response = await _client.put(
      ApiEndpoints.updateProfile,
      data: fields,
    );
    final data = response.data as Map<String, dynamic>;
    return UserModel.fromJson(data['user'] as Map<String, dynamic>? ?? data);
  }

  /// Log out — clears the server session (if applicable) and always
  /// clears the locally stored token, even if the network call fails,
  /// so the user is never stuck "logged in" on this device.
  Future<void> logout() async {
    try {
      await _client.post(ApiEndpoints.logout);
    } finally {
      await _client.clearAuthToken();
    }
  }

  /// Permanently delete the user's account.
  Future<void> deleteAccount() async {
    await _client.delete(ApiEndpoints.deleteAccount);
    await _client.clearAuthToken();
  }
}