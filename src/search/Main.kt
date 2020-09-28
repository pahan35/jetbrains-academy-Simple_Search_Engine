package search

import java.io.File
import java.util.*
import kotlin.system.exitProcess

interface Sourceable {
    fun populate(): List<String>
}

abstract class SearchStrategy {
    var tokenizedQuery: List<String> = emptyList()
    var invertedIndex: MutableMap<String, MutableList<Int>> = mutableMapOf()
    var size: Int = 0

    fun setData(query: String, invertedIndex: MutableMap<String, MutableList<Int>>, size: Int) {
        tokenizedQuery = query.toLowerCase().split(Regex("\\s+"))
        this.invertedIndex = invertedIndex
        this.size = size
    }

    fun findMatches(): List<MutableList<Int>> {
        return tokenizedQuery.mapNotNull { part -> invertedIndex[part] }
    }

    abstract fun find(): MutableList<Int>
}

class SearchStrategyAll : SearchStrategy() {
    override fun find(): MutableList<Int> {
        val matches = findMatches()
        if (matches.isEmpty()) {
            return mutableListOf()
        }
        return matches.reduce { acc, mutableList -> acc.intersect(mutableList).toMutableList() }
    }
}

open class SearchStrategyAny : SearchStrategy() {
    override fun find(): MutableList<Int> {
        val matches = findMatches()
        if (matches.isEmpty()) {
            return mutableListOf()
        }
        return matches.reduce { acc, mutableList -> acc.union(mutableList).toMutableList() }
    }
}

class SearchStrategyNone : SearchStrategyAny() {
    override fun find(): MutableList<Int> {
        val anyMatches = super.find()
        val matches = mutableListOf<Int>()
        for (i in 0 until size) {
            if (!anyMatches.contains(i)) {
                matches.add(i)
            }
        }
        return matches
    }
}

enum class Actions(val code: Int, private val label: String) {
    SEARCH(1, "Search information"),
    SHOW_ALL(2, "Print all data"),
    EXIT(0, "Exit");

    override fun toString(): String {
        return "$code. $label."
    }
}

enum class Strategies {
    ALL,
    ANY,
    NONE,
}

class CLIInputSource(private val scanner: Scanner) : Sourceable {
    override fun populate(): List<String> {
        println("Enter the number of people:")
        /**
         * @see https://www.geeksforgeeks.org/why-is-scanner-skipping-nextline-after-use-of-other-next-functions/
         */
        val peopleCount = scanner.nextLine().toInt()
        println("Enter all people:")
        return List(peopleCount) { scanner.nextLine() }
    }
}

class FileSource(private val fileName: String) : Sourceable {
    override fun populate(): List<String> {
        val file = File(fileName)
        return file.readLines()
    }
}

class InvertedIndex(private val people: List<String>) {
    private val invertedIndex = mutableMapOf<String, MutableList<Int>>()

    init {
        buildIndex()
    }

    private fun buildIndex() {
        people.forEachIndexed { index, person ->
            for (part in person.split(Regex("\\s+"))) {
                val lowerCasedPart = part.toLowerCase()
                if (!invertedIndex.containsKey(lowerCasedPart)) {
                    invertedIndex[lowerCasedPart] = mutableListOf()
                }
                invertedIndex[lowerCasedPart]!!.add(index)
            }
        }
    }

    fun find(query: String, strategy: Strategies): List<String> {
        val strategyInstance = when (strategy) {
            Strategies.ALL -> SearchStrategyAll()
            Strategies.ANY -> SearchStrategyAny()
            Strategies.NONE -> SearchStrategyNone()
        }
        strategyInstance.setData(query, invertedIndex, people.size)
        val result = strategyInstance.find()
        return result.map { people[it] }
    }
}

class Search(private val scanner: Scanner, source: Sourceable) {
    private val people: List<String> = source.populate()

    private val invertedIndex = InvertedIndex(people)

    fun start() {
        while (true) {
            printMenu()
            val actionCode = scanner.nextLine().toInt()
            println()
            runAction(Actions.values().find { it.code == actionCode })
            println()
        }
    }

    private fun askQuery() {
        println("Select a matching strategy: ${Strategies.values().joinToString(", ") { it.name }}")
        val strategy = Strategies.valueOf(scanner.nextLine())
        println("Enter a name or email to search all suitable people.")
        val query = scanner.nextLine().toLowerCase()
        findResult(query, strategy)
    }

    private fun findResult(query: String, strategy: Strategies) {
        printResult(invertedIndex.find(query, strategy))
    }

    private fun printResult(result: List<String> = emptyList()) {
        if (result.isEmpty()) {
            println("No matching people found.")
        } else {
            for (person in result) {
                println(person)
            }
        }
    }

    private fun printAllPeople() {
        println("=== List of people ===")
        people.forEach { println(it) }
    }

    private fun runAction(action: Actions?) {
        when (action) {
            Actions.SHOW_ALL -> printAllPeople()
            Actions.SEARCH -> askQuery()
            Actions.EXIT -> {
                println("Bye!")
                exitProcess(0)
            }
            null -> println("Incorrect option! Try again.")
        }
    }

    private fun printMenu() {
        println("=== Menu ===")
        for (action in listOf(Actions.SEARCH, Actions.SHOW_ALL, Actions.EXIT)) {
            println(action)
        }
    }
}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val fileSource = FileSource(args[1])
    val search = Search(scanner, fileSource)
    search.start()
}
