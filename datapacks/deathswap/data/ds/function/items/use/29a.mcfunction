execute as @s run function ds:items/use/all
give @s minecraft:milk_bucket
give @s minecraft:golden_apple 2

tellraw @s [{"text":"You have a milk bucket + 2 golden apples!","color":"white"}]
execute as @s at @s run playsound minecraft:entity.item.pickup master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use
