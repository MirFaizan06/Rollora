package design.techbytes.rollora
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import design.techbytes.rollora.ui.*

class MainActivity: FragmentActivity() {
 private lateinit var model: AppViewModel
 private var stoppedAt=0L
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); enableEdgeToEdge()
  window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  model=ViewModelProvider(this)[AppViewModel::class.java]
  setContent { RolloraRoot(model,this) }
 }
 override fun onStop() { super.onStop(); stoppedAt=SystemClock.elapsedRealtime() }
 override fun onStart() {
  super.onStart()
  if(::model.isInitialized && stoppedAt!=0L && SystemClock.elapsedRealtime()-stoppedAt>=30000) model.unlocked=false
 }
}
