execute as @s run function ds:items/use/all
give @s flint_and_steel
give @s iron_ingot

tellraw @s {"text":"You have flint & steel + iron ingot!","color":"gold"}
execute as @s at @s run playsound minecraft:entity.item.pickup master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use
