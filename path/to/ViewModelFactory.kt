class ViewModelFactory(private val userRepository: UserRepository, private val documentRepository: DocumentRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.getConstructor(UserRepository::class.java, DocumentRepository::class.java).newInstance(userRepository, documentRepository)
    }
}