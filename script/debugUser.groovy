import org.moqui.Moqui
import org.moqui.context.ExecutionContext

ExecutionContext ec = Moqui.getExecutionContext()
try {
    ec.user.loginUser("tf-admin", "moqui")
    println "Successfully logged in tf-admin"
    def user = ec.entity.find("moqui.security.UserAccount").condition("username", "tf-admin").one()
    println "User account: ${user}"
} catch (Exception e) {
    println "Login failed: ${e.message}"
    def users = ec.entity.find("moqui.security.UserAccount").list()
    println "All users: ${users.size()}"
    users.each { println " - ${it.username}" }
} finally {
    ec.destroy()
}
