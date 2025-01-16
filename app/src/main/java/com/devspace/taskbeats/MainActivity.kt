package com.devspace.taskbeats

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var categories = listOf<CategoryUiData>()
    private var categoriesEntity = listOf<CategoryEntity>()
    private var tasks = listOf<TaskUiData>()

    private lateinit var rvCategory: RecyclerView
    private lateinit var ctnEmptyView: LinearLayout
    private lateinit var fabCreateTask: FloatingActionButton

    private val categoryAdapter = CategoryListAdapter()
    private val taskAdapter by lazy {
        TaskListAdapter()
    }


    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            TaskBeatDataBase::class.java, "database-task-beat"
        ).build()
    }

    private val categoryDao: CategoryDao by lazy {
        db.getCategoryDao()
    }

    private val taskDao: TaskDao by lazy {
        db.getTaskDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvCategory = findViewById(R.id.rv_categories)
        ctnEmptyView = findViewById(R.id.ll_empty_view)
        val rvTask = findViewById<RecyclerView>(R.id.rv_tasks)
        fabCreateTask = findViewById(R.id.fab_create_task)
        val btnCreateEmpty = findViewById<Button>(R.id.btn_create_empty)

        btnCreateEmpty.setOnClickListener {
            showCreateCategoryBottomSheet()
        }

        fabCreateTask.setOnClickListener {
            showCreateUpdateTaskBottomSheet()
        }

        taskAdapter.setOnClickListener { task ->
            showCreateUpdateTaskBottomSheet(task)
        }

        categoryAdapter.setOnLongClickListener { categoryToBeDeleted ->
            if (categoryToBeDeleted.name != "+" && categoryToBeDeleted.name != "ALL") {

                val title: String = this.getString(R.string.category_delete_title)
                val description: String = this.getString(R.string.category_delete_description)
                val btnText: String = this.getString(R.string.delete)

                showInfoDialog(
                    title,
                    description,
                    btnText
                ) {
                    val categoryEntityToBeDeleted = CategoryEntity(
                        categoryToBeDeleted.name,
                        categoryToBeDeleted.isSelected
                    )
                    deleteCategory(categoryEntityToBeDeleted)

                }
            }
        }

        categoryAdapter.setOnClickListener { selected ->
            if (selected.name == "+") {
                showCreateCategoryBottomSheet()

            } else {
                val categoryTemp = categories.map { item ->
                    when {
                        item.name == selected.name && item.isSelected -> item.copy(isSelected = true)

                        item.name == selected.name && !item.isSelected -> item.copy(isSelected = true)
                        item.name != selected.name && item.isSelected -> item.copy(isSelected = false)
                        else -> item
                    }
                }

                if (selected.name != "ALL") {
                    filterTaskByCategoryName(selected.name)
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        getTasksFromDataBase()
                    }
                }

                categoryAdapter.submitList(categoryTemp)
            }
        }

        rvCategory.adapter = categoryAdapter
        lifecycleScope.launch(Dispatchers.IO) {
            getCategoriesFromDataBase()
        }

        rvTask.adapter = taskAdapter

        lifecycleScope.launch(Dispatchers.IO) {
            getTasksFromDataBase()
        }
    }

    private fun showInfoDialog(
        title: String,
        description: String,
        btnText: String,
        onClick: () -> Unit
    ) {
        val infoBottomSheet = InfoBottomSheet(
            title = title,
            description = description,
            btnText = btnText,
            onClick
        )

        infoBottomSheet.show(
            supportFragmentManager,
            "infoBottomSheet"
        )
    }

    private fun getCategoriesFromDataBase() {
        val categoriesFromDb: List<CategoryEntity> = categoryDao.getAll()
        categoriesEntity = categoriesFromDb

        lifecycleScope.launch(Dispatchers.Main) {
            if (categoriesEntity.isEmpty()) {
                rvCategory.isVisible = false
                fabCreateTask.isVisible = false
                ctnEmptyView.isVisible = true
            } else {
                rvCategory.isVisible = true
                fabCreateTask.isVisible = true
                ctnEmptyView.isVisible = false
            }
        }

        val categoriesUIData = categoriesFromDb.map {
            CategoryUiData(
                name = it.name,
                isSelected = it.isSelected
            )
        }.toMutableList()

        categoriesUIData.add(
            CategoryUiData(
                name = "+",
                isSelected = false
            )
        )

        val categoryListTemp = mutableListOf(
            CategoryUiData(
                name = "ALL",
                isSelected = false,
            )
        )

        categoryListTemp.addAll(categoriesUIData)
        lifecycleScope.launch(Dispatchers.Main) {
            categories = categoryListTemp
            withContext(Dispatchers.Main) {
                categoryAdapter.submitList(categories)
            }
        }
    }

    private fun getTasksFromDataBase() {
        val tasksFromDb: List<TaskEntity> = taskDao.getAll()
        val tasksUiData: List<TaskUiData> = tasksFromDb.map {
            TaskUiData(
                id = it.id,
                name = it.name,
                category = it.category
            )
        }

        lifecycleScope.launch(Dispatchers.Main) {
            tasks = tasksUiData
            withContext(Dispatchers.Main) {
                taskAdapter.submitList(tasksUiData)
            }
        }

    }

    private fun insertCategory(categoryEntity: CategoryEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            categoryDao.insert(categoryEntity)
            getCategoriesFromDataBase()
        }
    }

    private fun insertTask(taskEntity: TaskEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            taskDao.insert(taskEntity)
            getTasksFromDataBase()
        }
    }

    private fun updateTask(taskEntity: TaskEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            taskDao.update(taskEntity)
            getTasksFromDataBase()
        }
    }

    private fun deleteTask(taskEntity: TaskEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            taskDao.delete(taskEntity)
            getTasksFromDataBase()
        }
    }

    private fun deleteCategory(categoryEntity: CategoryEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tasksToBeDeleted = taskDao.getAllByCategoryName(categoryEntity.name)
            taskDao.deleteAll(tasksToBeDeleted)
            categoryDao.delete(categoryEntity)
            getCategoriesFromDataBase()
            getTasksFromDataBase()
        }
    }

    private fun filterTaskByCategoryName(category: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tasksFromDb: List<TaskEntity> = taskDao.getAllByCategoryName(category)
            val tasksUiData: List<TaskUiData> = tasksFromDb.map {
                TaskUiData(
                    id = it.id,
                    name = it.name,
                    category = it.category
                )
            }

            lifecycleScope.launch(Dispatchers.Main) {
                taskAdapter.submitList(tasksUiData)
            }
        }
    }

    private fun showCreateUpdateTaskBottomSheet(taskUiData: TaskUiData? = null) {
        val createTaskBottomSheet = CreateOrUpdateTaskBottomSheet(
            task = taskUiData,
            categoryList = categoriesEntity,
            onCreateClicked = { taskToBeCreated ->
                val taskEntityToBeInsert = TaskEntity(
                    name = taskToBeCreated.name,
                    category = taskToBeCreated.category
                )
                insertTask(taskEntityToBeInsert)
            },
            onUpdateClicked = { taskToBeUpdated ->
                val taskEntityToBeUpdate = TaskEntity(
                    id = taskToBeUpdated.id,
                    name = taskToBeUpdated.name,
                    category = taskToBeUpdated.category
                )
                updateTask(taskEntityToBeUpdate)

            },
            onUDeleteClicked = { taskToBeDeleted ->
                val taskEntityToBeDeleted = TaskEntity(
                    id = taskToBeDeleted.id,
                    name = taskToBeDeleted.name,
                    category = taskToBeDeleted.category
                )
                deleteTask(taskEntityToBeDeleted)
            }
        )

        createTaskBottomSheet.show(
            supportFragmentManager,
            "createTaskBottomSheet"
        )
    }

    private fun showCreateCategoryBottomSheet() {
        val createCategoryBottomSheet = CreateCategoryBottomSheet { categoryName ->
            val categoryEntity = CategoryEntity(
                name = categoryName,
                isSelected = false
            )

            insertCategory(categoryEntity)
        }
        createCategoryBottomSheet.show(supportFragmentManager, "createCategoryBottomSheet")
    }

}
