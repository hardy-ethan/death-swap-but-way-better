$execute as @p[scores={permPNo=$(s)}] at @s run function ds:items/misc/falling_anvils

$tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Spawned falling anvils above ","color":"white"},{"selector":"@p[scores={permPNo=$(s)}]","bold":false},{"text":"'s head","color":"white"}]

execute as @s run function ds:items/after_use