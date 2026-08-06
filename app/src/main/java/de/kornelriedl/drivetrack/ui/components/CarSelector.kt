package de.kornelriedl.drivetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.kornelriedl.drivetrack.data.Car

@Composable
fun CarSelector(
    cars: List<Car>,
    selectedCarId: Long?,
    onSelectCar: (Long?) -> Unit,
    onAddCar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var addDialogOpen by remember { mutableStateOf(false) }

    val selectedCar = cars.find { it.id == selectedCarId }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menuExpanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = selectedCar?.name ?: "Alle Autos",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Alle Autos") },
                onClick = {
                    onSelectCar(null)
                    menuExpanded = false
                }
            )
            if (cars.isNotEmpty()) Divider()
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    onClick = {
                        onSelectCar(car.id)
                        menuExpanded = false
                    }
                )
            }
            Divider()
            DropdownMenuItem(
                text = { Text("+ Neues Auto") },
                onClick = {
                    menuExpanded = false
                    addDialogOpen = true
                }
            )
        }
    }

    if (addDialogOpen) {
        AddCarDialog(
            onConfirm = onAddCar,
            onDismiss = { addDialogOpen = false }
        )
    }
}
