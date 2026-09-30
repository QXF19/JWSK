package moe.shizuku.manager

import android.app.Application
import com.topjohnwu.superuser.Shell

class JwskApplication : Application() {

    companion object {

        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(60)
            )
        }
    }

}
