scoreboard players set @s creativeMode 0
tag @s remove in_creativeMode
gamemode survival @s
tellraw @s {"text":">> Your creative mode abilities have expired! <<","color":"green"}
clear @s minecraft:command_block
clear @s minecraft:command_block_minecart
clear @s minecraft:repeating_command_block
clear @s minecraft:chain_command_block
clear @s minecraft:enchanted_golden_apple
execute as @s at @s run kill @e[type=item,nbt={Item:{id:"minecraft:enchanted_golden_apple"}},distance=..5]

title @s[tag=got_notch_apple] title " "
title @s[tag=got_notch_apple] subtitle {"text":"Your golden apple was returned!","color":"yellow"}
give @s[tag=got_notch_apple] enchanted_golden_apple
tellraw @s[tag=got_notch_apple] {"text":"*** You got your enchanted golden apple back as well! ***","color":"yellow","bold":true}
tag @s remove got_notch_apple