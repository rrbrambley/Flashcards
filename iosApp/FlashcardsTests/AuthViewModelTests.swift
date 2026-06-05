import XCTest
import Shared
@testable import Flashcards

@MainActor
final class AuthViewModelTests: XCTestCase {
    func test_submit_withEmptyFields_showsValidationError() async {
        let vm = AuthViewModel(authService: FakeAuthService())

        await vm.submit()

        XCTAssertEqual(vm.errorMessage, "Enter your email and password.")
    }

    func test_submit_login_passesTrimmedEmailAndClearsErrorOnSuccess() async {
        let fake = FakeAuthService()
        let vm = AuthViewModel(authService: fake)
        vm.email = "  user@example.com  "
        vm.password = "secret"

        await vm.submit()

        XCTAssertEqual(fake.lastLogin?.email, "user@example.com")
        XCTAssertNil(vm.errorMessage)
        XCTAssertFalse(vm.isSubmitting)
    }

    func test_submit_login_surfacesFailureMessage() async {
        let fake = FakeAuthService()
        fake.loginResult = AuthResult.Failure(message: "Invalid email or password.")
        let vm = AuthViewModel(authService: fake)
        vm.email = "user@example.com"
        vm.password = "wrong"

        await vm.submit()

        XCTAssertEqual(vm.errorMessage, "Invalid email or password.")
    }

    func test_submit_register_callsRegisterInRegisterMode() async {
        let fake = FakeAuthService()
        let vm = AuthViewModel(authService: fake)
        vm.toggleMode() // -> register
        vm.email = "new@example.com"
        vm.password = "secret"

        await vm.submit()

        XCTAssertEqual(fake.lastRegister?.email, "new@example.com")
        XCTAssertNil(fake.lastLogin)
    }

    func test_toggleMode_switchesTitlesAndClearsError() {
        let vm = AuthViewModel(authService: FakeAuthService())
        XCTAssertEqual(vm.submitTitle, "Log in")

        vm.toggleMode()

        XCTAssertEqual(vm.mode, .register)
        XCTAssertEqual(vm.submitTitle, "Create account")
        XCTAssertNil(vm.errorMessage)
    }
}
