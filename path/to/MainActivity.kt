import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: SessionBoundDocumentsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userRepository = UserRepository(Api(Gson()))
        val documentRepository = SessionBoundDocumentRepository(userRepository, DocumentApi(Api(Gson())))
        val viewModelFactory = ViewModelFactory(userRepository, documentRepository)
        viewModel = ViewModelProvider(this, viewModelFactory).get(SessionBoundDocumentsViewModel::class.java)

        viewModel.documents.observe(this, Observer {
            // Update UI with documents
        })

        viewModel.legacyUserId.observe(this, Observer {
            // Update UI with legacy user ID
        })

        viewModel.uploading.observe(this, Observer {
            // Update UI with upload progress
        })

        viewModel.uploadProgress.observe(this, Observer {
            // Update UI with upload progress
        })

        viewModel.deleteLoading.observe(this, Observer {
            // Update UI with delete loading state
        })

        viewModel.deleteSuccess.observe(this, Observer {
            // Update UI with delete success state
        })

        viewModel.deleteError.observe(this, Observer {
            // Update UI with delete error state
        })

        viewModel.isSessionStale.observe(this, Observer {
            // Update UI with session stale state
        })

        viewModel.isUploadStaged.observe(this, Observer {
            // Update UI with upload staged state
        })

        viewModel.isDeleteStaged.observe(this, Observer {
            // Update UI with delete staged state
        })

        viewModel.cancelUpload.observe(this, Observer {
            // Cancel upload
        })

        viewModel.cancelDelete.observe(this, Observer {
            // Cancel delete
        })
    }
}