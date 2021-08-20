package com.example.plugins

import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.html.*
import io.ktor.util.*
import java.lang.StringBuilder
import java.net.Inet4Address
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class Task(val id:Int, var name:String, var done:Boolean, var date:String) {
    override fun toString() = "{ id: " + id.toString() + ", name: \"" + name + "\", done: " + done.toString() + ", date : " + date.toString() + "}"
}

fun StringBuilder.addTasks(seq:List<Task>) {
    this.append("{ tasks: [")
    seq.forEach {
        this.append( it.toString() )
    }
    this.append("] }")
}

fun ResultSet.toList(): MutableList<Task> {
    val rs = this@toList
    /*
    val seq = rs.use {
         sequence<Task> {
            while (rs.next()) {
                yield(Task(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4)))
            }
        }
    }*/

    /*
    val seq = rs.use {
        generateSequence {
            if (rs.next()) Task(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4))
            else null
        }.toList()  // must be inside the use() block
    }
    */

    val lst = generateSequence {
                if (rs.next()) Task(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4))
                else null
        }.toMutableList()  // must be inside the use() block



    /*
    val lst = mutableListOf<Task>()
    while (rs.next()){
        lst.add( Task(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getString(4))  )
    }*/

    if(!rs.isClosed) rs.close()

    return lst
}

fun getConnection():Connection {

    Class.forName("org.postgresql.Driver");

    val localStringConn = "postgres://postgres:@127.0.0.1:80/test"

    var sslUse = "?sslmode=require"
    val env = System.getenv("DATABASE_URL")

    val dbUri = URI ( if(env == null){
        sslUse = ""
        localStringConn
    }else{ env } )

    //val dbUri = URI(System.getenv("DATABASE_URL") ?: localStringConn )

    //val dbUri = URI("postgres://vjvxwkhqjuamfz:3c0b80b92e0918fb1bccac2cb7ff27109c954d98a6974d404d00a053284e3a04@ec2-3-237-55-151.compute-1.amazonaws.com:5432/dcqfd4t5v0394n")

    println( "HOST: ${dbUri.getHost()}" )

    val username: String = dbUri.getUserInfo().split(":").get(0)
    val password: String = dbUri.getUserInfo().split(":").get(1)
    val dbUrl = "jdbc:postgresql://" +
            dbUri.getHost() + (if(sslUse!="") { ":" + dbUri.getPort() } else "") + dbUri.getPath().toString() + sslUse

    println( "username: $username" )
    println( "password: $password" )
    println( "sslUse: $sslUse" )
    println( "dbUrl: $dbUrl" )

    //log.info("dbUrl=$dbUrl  username=$username  password=$password")

    //println("$dbUrl $username $password ")
    return DriverManager.getConnection(dbUrl, username, password)
}

fun Application.configureRouting() {

    install(ContentNegotiation) {
        gson {
            //setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }

    install(IgnoreTrailingSlash) // игнор "/"


    var conn: Connection? = null
    conn = getConnection()

    log.info("connected!")



    val stm = conn.createStatement()
    //stm.execute("drop table if exists tasks;")
    //stm.createTableUsers()
    stm.execute("create table IF NOT EXISTS tasks (id serial primary key , name varchar (255), done bool, date varchar(50) );")

    // Starting point for a Ktor app:
    routing {
        get("/") {
            call.respondText("Welcome to Tasks API demo! loginx@mail")
        }

        get("tasks") {  // получим все задачи

            val rs = stm.executeQuery("select id, name, done, date from tasks")

            println("remoteHost: " + call.request.origin.remoteHost) // https://stackoverflow.com/questions/64482537/how-to-get-ip-address-from-call-object
            //Inet4Address.getAllByName(URL)

            val sb = StringBuilder()
            sb.addTasks( rs.toList())
            call.respondText(sb.toString())

            //sb.append("{ tasks: [")
            //rs.toSequence().forEach {
//                sb.append( it.toString() )
//            }
//            sb.append("] }")

/*
            val lst = sequence {
                while (rs.next()) {
                    yield( Task( rs.getInt(1), rs.getString(2), rs.getBoolean(3)) )
                }
            }.toList()
 */
        }

        get("tasks/done") { // получим все выполненые задачи
            val rs = stm.executeQuery("select id, name, done from tasks where done=true")
            val sb = StringBuilder()
            sb.addTasks( rs.toList())
            call.respondText(sb.toString())
        }

        get("tasks/undone") { // получим все не выполненые задачи
            val rs = stm.executeQuery("select id, name, done from tasks where done=false")
            val sb = StringBuilder()
            sb.addTasks( rs.toList())
            call.respondText(sb.toString())
        }

        get("tasks/add") { // добавим задачу

            val name = call.request.queryParameters["name"]
            val date = call.request.queryParameters["date"]
            val done = call.request.queryParameters["done"]

            val task = Task(0, "", false, "")
            task.name = name?.removeSurrounding("\"") ?: ""
            task.date = date?.removeSurrounding("\"") ?: ""
            task.done = done?.toBoolean() ?: false

            //done?.also { task.done = it.toBoolean() }

            stm.execute("insert into tasks(name, done, date) values (\'${task.name}\', ${task.done}, \'${task.date}\' )")

            val rs = stm.executeQuery("select id, name, done, date from tasks where id in (select max(id) from tasks)")
            val taskRes = rs.toList().firstOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respondText(taskRes.toString())
        }

        get("tasks/{id}") { // получим сведения о задаче. И возможно установим новые значения
            // name date done

            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

            val rs = stm.executeQuery("select id, name, done, date from tasks where id=$id")
            val task = rs.toList().firstOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

            val name = call.request.queryParameters["name"]
            val date = call.request.queryParameters["date"]
            val done = call.request.queryParameters["done"]

            //LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            //LocalDateTime.parse(it, DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH))

            name?.also { task.name = it }
            date?.also { task.date = it }
            done?.also { task.done = it.toBoolean() }

            stm.execute("update tasks set date=\'${task.date}\', name =\'${task.name}\',  done =${task.done}  where id=$id")

            call.respondText(task.toString())
        }

        get("tasks/{id}/delete") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

            val rs = stm.executeQuery("select id, name, done, date from tasks where id=$id")
            val task = rs.toList().firstOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

            stm.execute("delete from tasks where id=$id")

            call.respondText(task.toString())
        }

        get("tasks/web") {
            call.respondText("non implemented")
        }

        get("tasks/help") {

            call.respondText("""
                tasks
                tasks/done
                tasks/undone
                tasks/add?name="test"&date="24/10/11"
                tasks/1/
                tasks/1/?done=true & name = "test2"
                tasks/1/delete
                tasks/web
            """.trimIndent())
        }


    }

}
