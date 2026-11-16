import com.banking.models.Role;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.banking.utils.Money;
import com.banking.validators.InputValidator;

public class CoreSmokeTest {
    public static void main(String[] args) {
        if (Money.toMinor("100.05") != 10005) throw new AssertionError("money");
        if (Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.DEBIT)) throw new AssertionError("rbac");
        if (!InputValidator.email("  SOMEONE@example.com ").equals("someone@example.com")) throw new AssertionError("email");
        System.out.println("Core smoke tests passed");
    }
}
