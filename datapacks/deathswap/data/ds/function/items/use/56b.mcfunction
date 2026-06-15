$tag @p[scores={permPNo=$(s)}] add blockedItems
$scoreboard players set @p[scores={permPNo=$(s)}] blockedItems 15500
$title @p[scores={permPNo=$(s)}] title " "
$execute if score Lang Core matches 1 run title @p[scores={permPNo=$(s)}] subtitle {"text":">> You can't use items for 3 minutes! <<","color":"red"}
$execute if score Lang Core matches 2 run title @p[scores={permPNo=$(s)}] subtitle {"text":">> 您在三分钟内无法使用任何物品! <<","color":"red"}
$scoreboard players set @p[scores={permPNo=$(s)}] currItem 0
$execute as @p[scores={permPNo=$(s)}] run trigger select set 0
$tag @p[scores={permPNo=$(s)}] remove has_new_dye

$tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Blocked ","color":"aqua"},{"selector":"@p[scores={permPNo=$(s)}]","bold":false},{"text":" from using any items for 3 minutes","color":"aqua"}]

$tellraw @p[scores={permPNo=$(s)}] [{"text":">>> IMPORTANT: You can't use ANY items for 3 minutes because ","color":"red","bold":true},{"selector":"@s","bold":false},{"text":" used an item on you! <<<","color":"red","bold":true}]

$clear @p[scores={permPNo=$(s)}] white_dye
$clear @p[scores={permPNo=$(s)}] light_gray_dye
$clear @p[scores={permPNo=$(s)}] gray_dye
$clear @p[scores={permPNo=$(s)}] black_dye
$clear @p[scores={permPNo=$(s)}] brown_dye
$clear @p[scores={permPNo=$(s)}] red_dye
$clear @p[scores={permPNo=$(s)}] yellow_dye
$clear @p[scores={permPNo=$(s)}] orange_dye
$clear @p[scores={permPNo=$(s)}] lime_dye
$clear @p[scores={permPNo=$(s)}] green_dye
$clear @p[scores={permPNo=$(s)}] cyan_dye
$clear @p[scores={permPNo=$(s)}] light_blue_dye
$clear @p[scores={permPNo=$(s)}] blue_dye
$clear @p[scores={permPNo=$(s)}] purple_dye
$clear @p[scores={permPNo=$(s)}] magenta_dye
$clear @p[scores={permPNo=$(s)}] pink_dye

execute as @s run function ds:items/after_use