package com.seuapp.pokedex.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seuapp.pokedex.api.RetrofitClient
import com.seuapp.pokedex.domain.Pokemon
import com.seuapp.pokedex.domain.PokemonGeneration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PokemonListViewModel : ViewModel() {

    private val _pokemonListLiveData = MutableLiveData<List<Pokemon>>()
    val pokemonListLiveData: LiveData<List<Pokemon>> = _pokemonListLiveData

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var fullPokemonList: List<Pokemon> = emptyList()
    private var searchQuery: String = ""
    private var selectedTypeFilter: String? = null
    private var selectedGenerationFilter: PokemonGeneration? = null

    init {
        loadPokemonFromApi()
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        applyFilters()
    }

    fun updateTypeFilter(type: String?) {
        selectedTypeFilter = type?.lowercase()
        applyFilters()
    }

    fun updateGenerationFilter(generation: PokemonGeneration?) {
        selectedGenerationFilter = generation
        applyFilters()
    }

    private fun loadPokemonFromApi() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val pokemonList = withContext(Dispatchers.IO) {
                    val response = RetrofitClient.pokeApiService.getPokemonList(limit = 251)
                    coroutineScope {
                        response.results.map { result ->
                            async {
                                val id = result.url.split("/").dropLast(1).last().toInt()
                                val detail = runCatching {
                                    RetrofitClient.pokeApiService.getPokemonById(id)
                                }.getOrNull()

                                val types = detail?.types?.map { it.type.name } ?: emptyList()
                                val imageUrl = detail?.sprites?.frontDefault
                                    ?: "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$id.png"

                                Pokemon(
                                    id = id,
                                    name = formatName(result.name),
                                    imageUrl = imageUrl,
                                    types = types,
                                    generation = calculateGeneration(id)
                                )
                            }
                        }.awaitAll()
                    }.sortedBy { it.id }
                }

                fullPokemonList = pokemonList
                applyFilters()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Sem internet ou erro na sincronização: ${e.message}"
                _pokemonListLiveData.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applyFilters() {
        var filteredList = fullPokemonList

        if (searchQuery.isNotBlank()) {
            val queryLower = searchQuery.trim().lowercase()
            filteredList = filteredList.filter { it.name.lowercase().contains(queryLower) }
        }

        selectedTypeFilter?.let { type ->
            filteredList = filteredList.filter { pokemon ->
                pokemon.types.any { it.equals(type, ignoreCase = true) }
            }
        }

        selectedGenerationFilter?.let { generation ->
            filteredList = filteredList.filter { it.generation == generation }
        }

        _pokemonListLiveData.value = filteredList
    }

    private fun formatName(rawName: String): String {
        return rawName.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    private fun calculateGeneration(id: Int): PokemonGeneration {
        return when (id) {
            in 1..151 -> PokemonGeneration.GEN_I
            in 152..251 -> PokemonGeneration.GEN_II
            else -> PokemonGeneration.OTHER
        }
    }
}
