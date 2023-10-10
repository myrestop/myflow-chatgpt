import org.dizitart.no2.tool.ExportOptions
import org.dizitart.no2.tool.Exporter
import top.myrest.myflow.baseimpl.FlowApp
import top.myrest.myflow.baseimpl.enableDevEnv
import top.myrest.myflow.db.MyDb
import top.myrest.myflow.util.getCurrWorkDir

fun main() {
    enableDevEnv()
    FlowApp().openDb()
    val exporter = Exporter.of(MyDb.db)
    val options = ExportOptions()
    options.isExportIndices = true
    exporter.withOptions(options).exportTo(getCurrWorkDir("test.json"))
}
