package de.darkspirit510.converterdaemon.frontend

import de.darkspirit510.converterdaemon.service.TaskService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.math.round


@RestController
@RequestMapping("/")
class TaskController(
    private val taskService: TaskService
) {

    @GetMapping("/")
    fun index() = """<!DOCTYPE html>
<html>
<head>
    <title>Title</title>
    <style>
    
    </style>
    <script>
    function loadState() {
        var xhttp = new XMLHttpRequest();
        xhttp.onreadystatechange = function() {
            if (this.readyState == 4 && this.status == 200) {
                document.getElementById("state").innerHTML = this.responseText;
            }
        };
        xhttp.open("GET", "/state", true);
        xhttp.send();
        
        setTimeout(loadState, 2000)
    }
    </script>
</head>
<body onload="loadState()">
    <h1>ConverterDaemon</h1>
    <p id="state">-/-</p>
</body>
</html>"""

    @GetMapping("/state")
    fun state() = currentTask()

    private fun currentTask() = if (taskService.hasTask()) {
        "Converting ${taskService.task!!.sourceFile.name}, Frame ${taskService.currentFrame()}/${taskService.frameCount()} (${progress()}%)"
    } else {
        "Nothing to do..."
    }

    private fun progress() = round((taskService.currentFrame() / taskService.frameCount()).toDouble()).toInt()

}
