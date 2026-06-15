clear @s minecraft:command_block
clear @s minecraft:command_block_minecart
clear @s minecraft:repeating_command_block
clear @s minecraft:chain_command_block
clear @s minecraft:enchanted_golden_apple
kill @e[type=item,nbt={Item:{id:"minecraft:enchanted_golden_apple"}},distance=..5]
execute if score @s creativeMode matches 1.. run scoreboard players remove @s creativeMode 5
execute if score @s creativeMode matches ..5 run function ds:items/misc/exit_creative_mode